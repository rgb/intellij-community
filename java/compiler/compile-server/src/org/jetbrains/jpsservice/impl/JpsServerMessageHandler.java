package org.jetbrains.jpsservice.impl;

import org.jboss.netty.channel.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.server.BuildParameters;
import org.jetbrains.jps.server.BuildType;
import org.jetbrains.jps.server.Facade;
import org.jetbrains.jps.server.MessagesConsumer;
import org.jetbrains.jpsservice.JpsRemoteProto;
import org.jetbrains.jpsservice.Server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/11/11
 */
public class JpsServerMessageHandler extends SimpleChannelHandler {
  private final ConcurrentHashMap<String, CompilationTask> myBuildsInProgress = new ConcurrentHashMap<String, CompilationTask>();
  private final ExecutorService myBuildsExecutor;
  private final Server myServer;

  public JpsServerMessageHandler(ExecutorService buildsExecutor, Server server) {
    myBuildsExecutor = buildsExecutor;
    myServer = server;
  }

  public void messageReceived(final ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    final JpsRemoteProto.Message message = (JpsRemoteProto.Message)e.getMessage();
    final UUID sessionId = ProtoUtil.fromProtoUUID(message.getSessionId());

    JpsRemoteProto.Message reply = null;

    if (message.getMessageType() != JpsRemoteProto.Message.Type.REQUEST) {
      reply = ProtoUtil.toMessage(sessionId, ProtoUtil.createFailure("Cannot handle message " + message.toString()));
    }
    else if (!message.hasRequest()) {
      reply = ProtoUtil.toMessage(sessionId, ProtoUtil.createFailure("No request in message: " + message.toString()));
    }
    else {
      final JpsRemoteProto.Message.Request request = message.getRequest();
      final JpsRemoteProto.Message.Request.Type requestType = request.getRequestType();
      switch (requestType) {
        case COMPILE_REQUEST :
          reply = startBuild(sessionId, ctx, request.getCompileRequest());
          break;

        case SETUP_COMMAND:
          final Map<String, String> data = new HashMap<String, String>();
          final JpsRemoteProto.Message.Request.SetupCommand setupCommand = request.getSetupCommand();
          for (JpsRemoteProto.Message.Request.SetupCommand.PathVariable variable : setupCommand.getPathVariableList()) {
            data.put(variable.getName(), variable.getValue());
          }
          Facade.getInstance().setPathVariables(data);
          reply = ProtoUtil.toMessage(sessionId, ProtoUtil.createCommandCompletedEvent(null));
          break;

        case SHUTDOWN_COMMAND :
          // todo pay attention to policy
          myBuildsExecutor.submit(new Runnable() {
            public void run() {
              myServer.stop();
            }
          });
          break;

        default:
          reply = ProtoUtil.toMessage(sessionId, ProtoUtil.createFailure("Unknown request: " + message));
      }
    }
    if (reply != null) {
      Channels.write(ctx.getChannel(), reply);
    }
  }

  @Nullable
  private JpsRemoteProto.Message startBuild(UUID sessionId, final ChannelHandlerContext channelContext, JpsRemoteProto.Message.Request.CompilationRequest compileRequest) {
    if (!compileRequest.hasProjectId()) {
      return ProtoUtil.toMessage(sessionId, ProtoUtil.createFailure("No project specified"));
    }

    final String projectId = compileRequest.getProjectId();
    final JpsRemoteProto.Message.Request.CompilationRequest.Type compileType = compileRequest.getCommandType();

    switch (compileType) {
      case CLEAN:
      case MAKE:
      case REBUILD: {
        final CompilationTask task = new CompilationTask(sessionId, channelContext, projectId, compileRequest.getModuleNameList());
        if (myBuildsInProgress.putIfAbsent(projectId, task) == null) {
          task.getBuildParams().buildType = convertCompileType(compileType);
          task.getBuildParams().useInProcessJavac = true;
          myBuildsExecutor.submit(task);
        }
        else {
          return ProtoUtil.toMessage(sessionId, ProtoUtil.createFailure("Project is being compiled already"));
        }
        return null;
      }

      case CANCEL: {
        final CompilationTask task = myBuildsInProgress.get(projectId);
        if (task != null && task.getSessionId() == sessionId) {
          task.cancel();
        }
        return null;
      }

      default:
        return ProtoUtil.toMessage(sessionId, ProtoUtil.createFailure("Unsupported command: '" + compileType + "'"));
    }
  }

  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    super.exceptionCaught(ctx, e);
  }

  private class CompilationTask implements Runnable {

    private final UUID mySessionId;
    private final ChannelHandlerContext myChannelContext;
    private final String myProjectPath;
    private final Set<String> myModules;
    private final BuildParameters myParams;

    public CompilationTask(UUID sessionId, ChannelHandlerContext channelContext, String projectId, List<String> modules) {
      mySessionId = sessionId;
      myChannelContext = channelContext;
      myProjectPath = projectId;
      myModules = new HashSet<String>(modules);
      myParams = new BuildParameters();
    }

    public BuildParameters getBuildParams() {
      return myParams;
    }

    public UUID getSessionId() {
      return mySessionId;
    }

    public void run() {
      Channels.write(myChannelContext.getChannel(), ProtoUtil.toMessage(mySessionId, ProtoUtil.createBuildStartedEvent("build started")));
      Throwable error = null;
      try {
        Facade.getInstance().startBuild(myProjectPath, myModules, myParams, new MessagesConsumer() {
          public void consumeProgressMessage(String message) {
            Channels.write(
              myChannelContext.getChannel(), ProtoUtil.toMessage(mySessionId, ProtoUtil.createCompileProgressMessageResponse(message))
            );
          }

          public void consumeCompilerMessage(String compilerName, String message) {
            final JpsRemoteProto.Message.Response.CompileMessage.Kind kind =
              message.contains("error:")? JpsRemoteProto.Message.Response.CompileMessage.Kind.ERROR : JpsRemoteProto.Message.Response.CompileMessage.Kind.INFO;
            Channels.write(
              myChannelContext.getChannel(), ProtoUtil.toMessage(mySessionId, ProtoUtil.createCompileMessageResponse(kind, message, null, -1, -1))
            );
          }
        });
      }
      catch (Throwable e) {
        error = e;
      }
      finally {
        final JpsRemoteProto.Message lastMessage = error != null?
                  ProtoUtil.toMessage(mySessionId, ProtoUtil.createFailure("build failed: ", error)) :
                  ProtoUtil.toMessage(mySessionId, ProtoUtil.createBuildCompletedEvent("build completed"));

        Channels.write(myChannelContext.getChannel(), lastMessage).addListener(new ChannelFutureListener() {
          public void operationComplete(ChannelFuture future) throws Exception {
            myBuildsInProgress.remove(myProjectPath);
          }
        });
      }
    }

    public void cancel() {
      // todo
    }
  }

  private static BuildType convertCompileType(JpsRemoteProto.Message.Request.CompilationRequest.Type compileType) {
    switch (compileType) {
      case CLEAN: return BuildType.CLEAN;
      case MAKE: return BuildType.MAKE;
      case REBUILD: return BuildType.REBUILD;
    }
    return null;
  }
}
