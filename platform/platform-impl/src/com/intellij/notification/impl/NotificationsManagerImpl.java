/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.notification.impl;

import com.intellij.ide.FrameStateManager;
import com.intellij.notification.*;
import com.intellij.notification.impl.ui.NotificationsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PairFunction;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author spleaner
 */
public class NotificationsManagerImpl extends NotificationsManager implements Notifications, ApplicationComponent {

  private final NotificationModel myModel = new NotificationModel();

  @NotNull
  public String getComponentName() {
    return "NotificationsManager";
  }

  public void initComponent() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(TOPIC, this);
  }

  public static NotificationsManagerImpl getNotificationsManagerImpl() {
    return (NotificationsManagerImpl) ApplicationManager.getApplication().getComponent(NotificationsManager.class);
  }

  public void notify(@NotNull Notification notification) {
    doNotify(notification, NotificationDisplayType.BALLOON);
  }

  @Override
  public void register(@NotNull String groupDisplayType, @NotNull NotificationDisplayType defaultDisplayType) {
  }

  @Override
  public void expire(@NotNull Notification notification) {
    EventLog.expire(notification);
    remove(notification);
  }

  @Override
  public <T extends Notification> T[] getNotificationsOfType(Class<T> klass, @Nullable final Project project) {
    final List<Notification> notifications = myModel.getByType(null, createFilter(project, false));
    final List<T> result = new ArrayList<T>();
    for (final Notification notification : notifications) {
      if (klass.isInstance(notification)) {
        //noinspection unchecked
        result.add((T) notification);
      }
    }
    
    return ArrayUtil.toObjectArray(result, klass);
  }

  private static final PairFunction<Notification, Project, Boolean> ALL = new PairFunction<Notification, Project, Boolean>() {
    @NotNull
    public Boolean fun(final Notification notification, final Project project) {
      return true;
    }
  };

  private static final PairFunction<Notification, Project, Boolean> APPLICATION = new PairFunction<Notification, Project, Boolean>() {
    @NotNull
    public Boolean fun(final Notification notification, final Project project) {
      return project == null;
    }
  };

  public void clear(@Nullable Project project) {
    myModel.clear(createFilter(project, true));
  }

  public void disposeComponent() {
    myModel.clear(ALL);
  }

  protected void doNotify(@NotNull Notification notification, @Nullable final NotificationDisplayType displayType) {
    doNotify(notification, displayType, null);
  }

  public void doNotify(@NotNull final Notification notification, @Nullable NotificationDisplayType displayType, @Nullable final Project project) {
    final NotificationsConfiguration configuration = NotificationsConfiguration.getNotificationsConfiguration();
    if (!configuration.isRegistered(notification.getGroupId())) {
      configuration.registerDefaultSettings(new NotificationSettings(notification.getGroupId(),
                                                                     displayType == null ? NotificationDisplayType.BALLOON : displayType,
                                                                     true));
    }

    final NotificationSettings settings = NotificationsConfiguration.getSettings(notification.getGroupId());
    if (settings.isShouldLog() && settings.getDisplayType() != NotificationDisplayType.NONE) {
      myModel.add(notification, project);
    }

    showWhenVisible(notification, project);
  }

  private static void showWhenVisible(final Notification notification, final Project project) {
    FrameStateManager.getInstance().getApplicationActive().doWhenDone(new Runnable() {
      @Override
      public void run() {
        if (project != null) {
          if (project.isDisposed()) {
            return;
          }

          if (!project.isInitialized()) {
            StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
              @Override
              public void run() {
                showWhenVisible(notification, project);
              }
            });
            return;
          }
        }
        showNotification(notification, project);
      }
    });
  }

  private static void showNotification(final Notification notification, @Nullable final Project project) {
    String groupId = notification.getGroupId();
    final NotificationSettings settings = NotificationsConfiguration.getSettings(groupId);

    if (EventLog.isEventLogVisible(project) && settings.isShouldLog()) {
      return;
    }

    NotificationDisplayType type = settings.getDisplayType();
    String toolWindowId = NotificationsConfiguration.getNotificationsConfiguration().getToolWindowId(groupId);
    if (type == NotificationDisplayType.TOOL_WINDOW &&
        (toolWindowId == null || project == null || !Arrays.asList(ToolWindowManager.getInstance(project).getToolWindowIds()).contains(toolWindowId))) {
      type = NotificationDisplayType.BALLOON;
    }
    if (type == NotificationDisplayType.BALLOON && ProjectManager.getInstance().getOpenProjects().length == 0) {
      type = NotificationDisplayType.STICKY_BALLOON;
    }

    switch (type) {
      case NONE:
        return;
      //case EXTERNAL:
      //  notifyByExternal(notification);
      //  break;
      case STICKY_BALLOON:
      case BALLOON:
      default:
        notifyByBalloon(notification, type, project);
        break;
      case TOOL_WINDOW:
        MessageType messageType = notification.getType() == NotificationType.ERROR
                            ? MessageType.ERROR
                            : notification.getType() == NotificationType.WARNING ? MessageType.WARNING : MessageType.INFO;
        final NotificationListener notificationListener = notification.getListener();
        HyperlinkListener listener = notificationListener == null ? null : new HyperlinkListener() {
          @Override
          public void hyperlinkUpdate(HyperlinkEvent e) {
            notificationListener.hyperlinkUpdate(notification, e);
          }
        };
        assert toolWindowId != null;
        String msg = StringUtil.isEmpty(notification.getTitle()) ? notification.getContent() : notification.getTitle();
        //noinspection SSBasedInspection
        ToolWindowManager.getInstance(project).notifyByBalloon(toolWindowId, messageType, msg, notification.getIcon(), listener);
    }
  }

  private static void notifyByBalloon(final Notification notification,
                                      final NotificationDisplayType displayType,
                                      @Nullable final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    Window window = findWindowForBalloon(project);
    if (window instanceof IdeFrameImpl) {
      boolean sticky = NotificationDisplayType.STICKY_BALLOON == displayType;
      Balloon balloon = createBalloon(notification, false, !sticky, !sticky);
      Disposer.register(project != null ? project : ApplicationManager.getApplication(), balloon);
      ((IdeFrameImpl)window).getBalloonLayout().add(balloon);
    }
  }

  public static Window findWindowForBalloon(Project project) {
    return WindowManager.getInstance().getFrame(project);
  }

  public static Balloon createBalloon(final Notification notification, final boolean showCallout, final boolean hideOnClickOutside, final boolean fadeOut) {
    final JEditorPane text = new JEditorPane();
    text.setEditorKit(UIUtil.getHTMLEditorKit());

    final HyperlinkListener listener = NotificationsUtil.wrapListener(notification);
    if (listener != null) {
      text.addHyperlinkListener(listener);
    }

    final JLabel label = new JLabel(NotificationsUtil.buildHtml(notification, null));
    text.setText(NotificationsUtil.buildHtml(notification, "width:" + Math.min(400, label.getPreferredSize().width) + "px;"));
    text.setEditable(false);
    text.setOpaque(false);

    if (UIUtil.isUnderNimbusLookAndFeel()) {
      text.setBackground(new Color(0, 0, 0, 0));
    }

    text.setBorder(null);

    final JPanel content = new NonOpaquePanel(new BorderLayout((int)(label.getIconTextGap() * 1.5), (int)(label.getIconTextGap() * 1.5)));

    final NonOpaquePanel textWrapper = new NonOpaquePanel(new GridBagLayout());
    textWrapper.add(text);
    content.add(textWrapper, BorderLayout.CENTER);

    final NonOpaquePanel north = new NonOpaquePanel(new BorderLayout());
    north.add(new JLabel(NotificationsUtil.getIcon(notification)), BorderLayout.NORTH);
    content.add(north, BorderLayout.WEST);

    content.setBorder(new EmptyBorder(2, 4, 2, 4));

    final BalloonBuilder builder = JBPopupFactory.getInstance().createBalloonBuilder(content);
    builder.setFillColor(NotificationsUtil.getBackground(notification)).setCloseButtonEnabled(true).setShowCallout(showCallout)
      .setHideOnClickOutside(hideOnClickOutside)
      .setHideOnKeyOutside(hideOnClickOutside).setHideOnFrameResize(false);

    if (fadeOut) {
      builder.setFadeoutTime(3000);
    }

    final Balloon balloon = builder.createBalloon();
    balloon.addListener(new JBPopupAdapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        notification.setBalloon(null);
      }
    });

    notification.setBalloon(balloon);
    return balloon;
  }

  private static PairFunction<Notification, Project, Boolean> createFilter(@Nullable final Project project, final boolean strict) {
    return project == null ? APPLICATION : new ProjectFilter(project, strict);
  }

  @Nullable
  public Notification remove(Notification notification) {
    return myModel.remove(notification);
  }

  public Collection<Notification> getByType(@Nullable final NotificationType type, @Nullable final Project project) {
    return myModel.getByType(type, createFilter(project, false));
  }

  private static class ProjectFilter implements PairFunction<Notification, Project, Boolean> {
    private final Project myProject;
    private final boolean myStrict;

    private ProjectFilter(@NotNull final Project project, final boolean strict) {
      myProject = project;
      myStrict = strict;
    }

    @NotNull
    public Boolean fun(final Notification notification, final Project project) {
      return myStrict ? project == myProject : project == null || project == myProject;
    }
  }
}
