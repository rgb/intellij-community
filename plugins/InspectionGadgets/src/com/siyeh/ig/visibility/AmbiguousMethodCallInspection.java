/*
 * Copyright 2008 Bas Leijdekkers
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
package com.siyeh.ig.visibility;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AmbiguousMethodCallInspection extends BaseInspection {

  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "ambiguous.method.call.display.name");
  }

  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiClass superClass = (PsiClass)infos[0];
    final PsiClass outerClass = (PsiClass)infos[1];
    return InspectionGadgetsBundle.message(
      "ambiguous.method.call.problem.descriptor",
      superClass.getName(), outerClass.getName());
  }

  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new AmbiguousMethodCallFix();
  }

  private static class AmbiguousMethodCallFix extends InspectionGadgetsFix {

    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "ambiguous.method.call.quickfix");
    }

    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)parent.getParent();
      final String newExpressionText =
        "this." + methodCallExpression.getText();
      replaceExpression(methodCallExpression, newExpressionText);
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new AmbiguousMethodCallVisitor();
  }

  private static class AmbiguousMethodCallVisitor
    extends BaseInspectionVisitor {

    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier != null) {
        return;
      }
      PsiClass containingClass =
        ClassUtils.getContainingClass(expression);
      if (containingClass == null) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass methodClass = method.getContainingClass();
      if (!containingClass.isInheritor(methodClass, true)) {
        return;
      }
      containingClass = ClassUtils.getContainingClass(containingClass);
      final String methodName = methodExpression.getReferenceName();
      while (containingClass != null) {
        final PsiMethod[] methods =
          containingClass.findMethodsByName(methodName, false);
        if (methods.length > 0 &&
            !methodClass.equals(containingClass)) {
          registerMethodCallError(expression, methodClass,
                                  containingClass);
          return;
        }
        containingClass =
          ClassUtils.getContainingClass(containingClass);
      }
    }
  }
}