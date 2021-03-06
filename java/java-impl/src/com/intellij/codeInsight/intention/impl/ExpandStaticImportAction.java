/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.psi.util.ImportsUtil.*;

public class ExpandStaticImportAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#" + ExpandStaticImportAction.class.getName());
  private static final String REPLACE_THIS_OCCURRENCE = "Replace this occurrence and keep the method";
  private static final String REPLACE_ALL_AND_DELETE_IMPORT = "Replace all and delete the import";

  @NotNull
  public String getFamilyName() {
    return "Expand Static Import";
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!PsiUtil.isLanguageLevel5OrHigher(element)) return false;
    final PsiElement parent = element.getParent();
    if (!(element instanceof PsiIdentifier) || !(parent instanceof PsiJavaCodeReferenceElement)) {
      return false;
    }
    final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)parent;
    final PsiElement resolveScope = referenceElement.advancedResolve(true).getCurrentFileResolveScope();
    if (resolveScope instanceof PsiImportStaticStatement) {
      final PsiClass targetClass = ((PsiImportStaticStatement)resolveScope).resolveTargetClass();
      if (targetClass == null) return false;
      setText("Expand static import to " + targetClass.getName() + "." + referenceElement.getReferenceName());
      return true;
    }
    return false;
  }

  public void invoke(final Project project, final PsiFile file, final Editor editor, PsiElement element) {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;

    final PsiJavaCodeReferenceElement refExpr = (PsiJavaCodeReferenceElement)element.getParent();

    final PsiImportStaticStatement staticImport = (PsiImportStaticStatement)refExpr.advancedResolve(true).getCurrentFileResolveScope();


    final List<PsiJavaCodeReferenceElement> expressionToExpand = collectReferencesThrough(file, refExpr, staticImport);


    if (expressionToExpand.isEmpty()) {
      expand(refExpr, staticImport);
      staticImport.delete();
    }
    else {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        replaceAllAndDeleteImport(expressionToExpand, refExpr, staticImport);
      }
      else {
        final BaseListPopupStep<String> step =
          new BaseListPopupStep<String>("Multiple Similar Calls Found",
                                        new String[]{REPLACE_THIS_OCCURRENCE, REPLACE_ALL_AND_DELETE_IMPORT}) {
            @Override
            public PopupStep onChosen(final String selectedValue, boolean finalChoice) {
              new WriteCommandAction(project, ExpandStaticImportAction.this.getText()) {
                @Override
                protected void run(Result result) throws Throwable {
                  if (selectedValue == REPLACE_THIS_OCCURRENCE) {
                    expand(refExpr, staticImport);
                  }
                  else {
                    replaceAllAndDeleteImport(expressionToExpand, refExpr, staticImport);
                  }
                }
              }.execute();
              return FINAL_CHOICE;
            }
          };
        JBPopupFactory.getInstance().createListPopup(step).showInBestPositionFor(editor);
      }
    }
  }

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    invoke(project, file, editor, element);
  }
}
