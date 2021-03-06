package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Getter;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

/**
* Created by IntelliJ IDEA.
* User: zajac
* Date: 05.03.11
* Time: 10:40
* To change this template use File | Settings | File Templates.
*/
public class PrevOccurrenceAction extends EditorHeaderAction implements DumbAware {

  public PrevOccurrenceAction(EditorSearchComponent editorSearchComponent, Getter<JTextComponent> editorTextField) {
    super(editorSearchComponent);

    copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_OCCURENCE));

    ArrayList<Shortcut> shortcuts = new ArrayList<Shortcut>();
    ContainerUtil.addAll(shortcuts, ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_PREVIOUS).getShortcutSet().getShortcuts());
    if (!editorSearchComponent.getFindModel().isMultiline()) {
      ContainerUtil.addAll(shortcuts,
                           ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP).getShortcutSet().getShortcuts());

      shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), null));
    }
    registerShortcutsForComponent(shortcuts, editorTextField.get(), this);
  }

  public void actionPerformed(final AnActionEvent e) {
    getEditorSearchComponent().searchBackward();
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(getEditorSearchComponent().hasMatches());
  }
}
