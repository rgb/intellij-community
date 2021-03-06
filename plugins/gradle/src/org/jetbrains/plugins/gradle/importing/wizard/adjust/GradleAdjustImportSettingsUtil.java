package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.Named;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * @author Denis Zhdanov
 * @since 8/24/11 2:40 PM
 */
public class GradleAdjustImportSettingsUtil {

  private GradleAdjustImportSettingsUtil() {
  }

  /**
   * Setups given builder to expose controls for management of the given component's name.
   *
   * @param builder    target settings builder
   * @param component  component which name management should be exposed
   * @return    UI control that holds target component's name
   */
  @NotNull
  public static JComponent configureNameControl(@NotNull GradleProjectSettingsBuilder builder, @NotNull final Named component) {
    final JTextField result = new JTextField();
    result.setText(component.getName());
    builder.add("gradle.import.structure.settings.label.name", result);
    result.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        applyNewName();
      }
      @Override
      public void removeUpdate(DocumentEvent e) {
        applyNewName();
      }
      @Override
      public void changedUpdate(DocumentEvent e) {
      }
      private void applyNewName() {
        String text = result.getText();
        if (text == null) {
          return;
        }
        component.setName(text.trim());
      }
    });
    return result;
  }

  /**
   * Performs generic check of the name of the given component.
   * 
   * @param namedComponent  target component
   * @param componentNameUI UI control that allow to manage target component's name
   * @return                <code>true</code> if validation is successful; <code>false</code> otherwise
   */
  public static boolean validate(@NotNull Named namedComponent, @NotNull JComponent componentNameUI) {
    if (!StringUtil.isEmptyOrSpaces(namedComponent.getName())) {
      return true;
    }
    GradleUtil.showBalloon(componentNameUI, MessageType.ERROR, GradleBundle.message("gradle.import.text.error.undefined.name"));
    return false;
  }
}
