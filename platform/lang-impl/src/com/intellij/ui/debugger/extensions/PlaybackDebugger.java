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

package com.intellij.ui.debugger.extensions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.ex.FileChooserKeys;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.PlaybackRunner;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.debugger.UiDebuggerExtension;
import com.intellij.util.WaitFor;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

public class PlaybackDebugger implements UiDebuggerExtension, PlaybackRunner.StatusCallback {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.debugger.extensions.PlaybackDebugger");

  private static final Color ERROR_COLOR = Color.RED;
  private static final Color MESSAGE_COLOR = Color.BLACK;
  private static final Color CODE_COLOR = Color.BLUE;
  private static final Color TEST_COLOR = Color.green.darker();

  private JPanel myComponent;

  private PlaybackRunner myRunner;

  private JEditorPane myLog;

  private final JTextField myScriptsPath = new JTextField();

  private static final String EXT = "ijs";

  private static final String DOT_EXT = "." + EXT;

  private final JTextField myCurrentScript = new JTextField();

  private VirtualFileAdapter myVfsListener;

  private boolean myChanged;

  private PlaybackDebuggerState myState;
  private static final FileChooserDescriptor FILE_DESCRIPTOR = new ScriptFileChooserDescriptor();
  private JTextArea myCodeEditor;

  private void initUi() {
    myComponent = new JPanel(new BorderLayout());
    myLog = new JEditorPane();
    myLog.setEditorKit(new StyledEditorKit());
    myLog.setEditable(false);


    myState = ServiceManager.getService(PlaybackDebuggerState.class);

    final DefaultActionGroup controlGroup = new DefaultActionGroup();
    controlGroup.add(new RunOnFameActivationAction());
    controlGroup.add(new ActivateFrameAndRun());
    controlGroup.add(new StopAction());

    JPanel north = new JPanel(new BorderLayout());
    north.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, controlGroup, true).getComponent(), BorderLayout.WEST);

    final JPanel right = new JPanel(new BorderLayout());
    right.add(myCurrentScript, BorderLayout.CENTER);
    myCurrentScript.setText(myState.currentScript);
    myCurrentScript.setEditable(false);

    final DefaultActionGroup fsGroup = new DefaultActionGroup();
    SaveAction saveAction = new SaveAction();
    saveAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("control S")), myComponent);
    fsGroup.add(saveAction);
    SetScriptFileAction setScriptFileAction = new SetScriptFileAction();
    setScriptFileAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("control O")), myComponent);
    fsGroup.add(setScriptFileAction);
    AnAction newScriptAction = new NewScriptAction();
    newScriptAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("control N")), myComponent);
    fsGroup.add(newScriptAction);

    final ActionToolbar tb = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, fsGroup, true);
    tb.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    right.add(tb.getComponent(), BorderLayout.EAST);
    north.add(right, BorderLayout.CENTER);

    myComponent.add(north, BorderLayout.NORTH);

    myCodeEditor = new JTextArea();
    myCodeEditor.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        myChanged = true;
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        myChanged = true;
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        myChanged = true;
      }
    });
    if (pathToFile() != null) {
      loadFrom(pathToFile());
    }

    final Splitter script2Log = new Splitter(true);
    script2Log.setFirstComponent(ScrollPaneFactory.createScrollPane(myCodeEditor));

    script2Log.setSecondComponent(ScrollPaneFactory.createScrollPane(myLog));

    myComponent.add(script2Log, BorderLayout.CENTER);

    myVfsListener = new VirtualFileAdapter() {
      @Override
      public void contentsChanged(VirtualFileEvent event) {
        final VirtualFile file = pathToFile();
        if (file != null && file.equals(event.getFile())) {
          loadFrom(event.getFile());
        }
      }
    };
    LocalFileSystem.getInstance().addVirtualFileListener(myVfsListener);
  }

  private class SaveAction extends AnAction {
    private SaveAction() {
      super("Save", "", IconLoader.getIcon("/actions/menu-saveall.png"));
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myChanged);
    }

    public void actionPerformed(AnActionEvent e) {
      if (pathToFile() == null) {
        VirtualFile selectedFile = FileChooser.chooseFile(myComponent, FILE_DESCRIPTOR, null);
        if (selectedFile != null) {
          myState.currentScript = selectedFile.getPresentableUrl();
          myCurrentScript.setText(myState.currentScript);
        }
        else {
          Messages.showErrorDialog("File to save is not selected.", "Cannot save script");
          return;
        }
      }
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          save();
        }
      });
    }
  }

  private static class ScriptFileChooserDescriptor extends FileChooserDescriptor {
    public ScriptFileChooserDescriptor() {
      super(true, false, false, false, false, false);
      putUserData(FileChooserKeys.NEW_FILE_TYPE, UiScriptFileType.getInstance());
      putUserData(FileChooserKeys.NEW_FILE_TEMPLATE_TEXT, "");
    }

    @Override
    public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
      if (!showHiddenFiles && FileElement.isFileHidden(file)) return false;
      return file.getExtension() != null && file.getExtension().equalsIgnoreCase(UiScriptFileType.myExtension)
             || super.isFileVisible(file, showHiddenFiles) && file.isDirectory();
    }
  }

  private class SetScriptFileAction extends AnAction {

    private SetScriptFileAction() {
      super("Set Script File", "", IconLoader.getIcon("/nodes/packageOpen.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      VirtualFile selectedFile = FileChooser.chooseFile(myComponent, FILE_DESCRIPTOR, pathToFile());
      if (selectedFile != null) {
        myState.currentScript = selectedFile.getPresentableUrl();
        loadFrom(selectedFile);
        myCurrentScript.setText(myState.currentScript);
      }
    }
  }

  private class NewScriptAction extends AnAction {
    private NewScriptAction() {
      super("New Script", "", IconLoader.getIcon("/actions/new.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myState.currentScript = "";
      myCurrentScript.setText(myState.currentScript);
      fillDocument("");
    }
  }

  private void fillDocument(final String text) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myCodeEditor.setText(text == null ? "" : text);
      }
    });
  }

  @Nullable
  private VirtualFile pathToFile() {
    if (myState.currentScript.length() == 0) {
      return null;
    }
    return LocalFileSystem.getInstance().findFileByPath(myState.currentScript);
  }

  private void save() {
    try {
      VirtualFile file = pathToFile();
      final String toWrite = myCodeEditor.getText();
      String text = toWrite != null ? toWrite : "";
      VfsUtil.saveText(file, text);
      myChanged = false;
    }
    catch (IOException e) {
      Messages.showErrorDialog(e.getMessage(), "Cannot save script");
    }
  }

  private void loadFrom(@NotNull VirtualFile file) {
    try {
      final String text = CharsetToolkit.bytesToString(file.contentsToByteArray());
      fillDocument(text);
      myChanged = false;
    }
    catch (IOException e) {
      Messages.showErrorDialog(e.getMessage(), "Cannot load file");
    }
  }

  private File getScriptsFile() {
    final String text = myScriptsPath.getText();
    if (text == null) return null;

    final File file = new File(text);
    return file.exists() ? file : null;
  }

  private class StopAction extends AnAction {
    private StopAction() {
      super("Stop", null, IconLoader.getIcon("/actions/suspend.png"));
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myRunner != null);
    }

    public void actionPerformed(AnActionEvent e) {
      if (myRunner != null) {
        myRunner.stop();
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            myRunner = null;
          }
        });
      }
    }
  }

  private class ActivateFrameAndRun extends AnAction {
    private ActivateFrameAndRun() {
      super("Activate Frame And Run", "", IconLoader.getIcon("/nodes/deploy.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      activateAndRun();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myRunner == null);
    }
  }

  private class RunOnFameActivationAction extends AnAction {

    private RunOnFameActivationAction() {
      super("Run On Frame Activation", "", IconLoader.getIcon("/general/run.png"));
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myRunner == null);
    }

    public void actionPerformed(AnActionEvent e) {
      runOnFrame();
    }
  }

  private void activateAndRun() {
    assert myRunner == null;

    myLog.setText(null);

    final IdeFrameImpl frame = getFrame();

    final Component c = ((WindowManagerEx)WindowManager.getInstance()).getFocusedComponent(frame);

    if (c != null) {
      c.requestFocus();
    } else {
      frame.requestFocus();
    }

    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        startWhenFrameActive();
      }
    });

  }

  private IdeFrameImpl getFrame() {
    final Frame[] all = Frame.getFrames();
    for (Frame each : all) {
      if (each instanceof IdeFrame) {
        return (IdeFrameImpl)each;
      }
    }

    throw new IllegalStateException("Cannot find IdeFrame to run on");
  }

  private void runOnFrame() {
    assert myRunner == null;

    startWhenFrameActive();
  }

  private void startWhenFrameActive() {
    myLog.setText(null);

    addInfo("Waiting for IDE frame activation", -1, MESSAGE_COLOR, 0);
    myRunner = new PlaybackRunner(myCodeEditor.getText(), this, false, true);
    VirtualFile file = pathToFile();
    if (file != null) {
      VirtualFile scriptDir = file.getParent();
      if (scriptDir != null) {
        myRunner.setScriptDir(new File(scriptDir.getPresentableUrl()));
      }
    }

    new Thread() {
      @Override
      public void run() {
        new WaitFor() {
          protected boolean condition() {
            return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() instanceof IdeFrame || myRunner == null;
          }
        };                                            

        if (myRunner == null) {
          message(null, "Script stopped", -1, Type.message, true);
          return;
        }

        message(null, "Starting script...", -1, Type.message, true);

        try {
          sleep(1000);
        }
        catch (InterruptedException e) {}


        if (myRunner == null) {
          message(null, "Script stopped", -1, Type.message, true);
          return;
        }

        final PlaybackRunner runner = myRunner;

        myRunner.run().doWhenProcessed(new Runnable() {
          public void run() {
            if (runner == myRunner) {
              SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                  myRunner = null;
                }
              });
            }
          }
        });
      }
    }.start();
  }

  public void message(@Nullable final PlaybackContext context, final String text, final Type type) {
    message(context, text, context != null ? context.getCurrentLine() : -1, type, false);
  }

  private void message(@Nullable final PlaybackContext context, final String text, final int currentLine, final Type type, final boolean forced) {
    final int depth = context != null ? context.getCurrentStageDepth() : 0;
    
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (!forced && (context != null && context.isDisposed())) return;

        switch (type) {
          case message:
            addInfo(text, currentLine, MESSAGE_COLOR, depth);
            break;
          case error:
            addInfo(text, currentLine, ERROR_COLOR, depth);
            break;
          case code:
            addInfo(text, currentLine, CODE_COLOR, depth);
            break;
          case test:
            addInfo(text, currentLine, TEST_COLOR, depth);
            break;
        }
      }
    });
  }

  public JComponent getComponent() {
    if (myComponent == null) {
      initUi();
    }

    return myComponent;
  }

  public String getName() {
    return "Playback";
  }

  public void dispose() {
    disposeUiResources();
  }

  @State(
      name = "PlaybackDebugger",
      storages = {
          @Storage(
              file="$APP_CONFIG$/other.xml")}
  )
  public static class PlaybackDebuggerState implements PersistentStateComponent<Element> {
    private static final String ATTR_CURRENT_SCRIPT = "currentScript";
    public String currentScript = "";

    public Element getState() {
      final Element element = new Element("playback");
      element.setAttribute(ATTR_CURRENT_SCRIPT, currentScript);
      return element;
    }

    public void loadState(Element state) {
      final String path = state.getAttributeValue(ATTR_CURRENT_SCRIPT);
      if (path != null) {
        currentScript = path;
      }
    }
  }

  public void disposeUiResources() {
    myComponent = null;
    LocalFileSystem.getInstance().removeVirtualFileListener(myVfsListener);
    myCurrentScript.setText("");
    myLog.setText(null);
  }

  private void addInfo(String text, int line, Color fg, int depth) {
    if (text == null || text.length() == 0) return;

    String inset = StringUtil.repeat("   ", depth);

    Document doc = myLog.getDocument();
    SimpleAttributeSet attr = new SimpleAttributeSet();
    StyleConstants.setFontFamily(attr, UIManager.getFont("Label.font").getFontName());
    StyleConstants.setFontSize(attr, UIManager.getFont("Label.font").getSize());
    StyleConstants.setForeground(attr, fg);
    try {
      doc.insertString(doc.getLength(), inset + text + "\n", attr);
    }
    catch (BadLocationException e) {
      LOG.error(e);
    }
    scrollToLast();
  }

  private void scrollToLast() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (myLog.getDocument().getLength() == 0) return;

        Rectangle bounds = myLog.getBounds();
        myLog.scrollRectToVisible(new Rectangle(0, (int)bounds.getMaxY() - 1, (int)bounds.getWidth(), 1));
      }
    });
  }

}
