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
package org.jetbrains.plugins.groovy.template;

import com.intellij.codeInsight.template.EverywhereContextType;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionData;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author peter
 */
public abstract class GroovyTemplateContextType extends TemplateContextType {

  protected GroovyTemplateContextType(@NotNull @NonNls String id,
                                @NotNull String presentableName,
                                @Nullable Class<? extends TemplateContextType> baseContextType) {
    super(id, presentableName, baseContextType);
  }

  public boolean isInContext(@NotNull final PsiFile file, final int offset) {
    if (PsiUtilBase.getLanguageAtOffset(file, offset).isKindOf(GroovyFileType.GROOVY_LANGUAGE)) {
      PsiElement element = file.findElementAt(offset);
      if (element instanceof PsiWhiteSpace) {
        return false;
      }
      return element != null && isInContext(element);
    }

    return false;
  }

  protected abstract boolean isInContext(@NotNull PsiElement element);

  @Override
  public boolean isInContext(@NotNull final FileType fileType) {
    return false;
  }

  public static class Generic extends GroovyTemplateContextType {
    public Generic() {
      super("GROOVY", "Groovy", EverywhereContextType.class);
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      return true;
    }
  }

  public static class Statement extends GroovyTemplateContextType {
    public Statement() {
      super("GROOVY_STATEMENT", "Statement", Generic.class);
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      return isStatementContext(element);
    }

    private static boolean isStatementContext(PsiElement element) {
      return Expression.isExpressionContext(element) && PsiUtil.isExpressionStatement(element.getParent());
    }
  }
  public static class Expression extends GroovyTemplateContextType {

    public Expression() {
      super("GROOVY_EXPRESSION", "Expression", Generic.class);
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      return isExpressionContext(element);
    }

    private static boolean isExpressionContext(PsiElement element) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof GrReferenceExpression)) {
        return false;
      }
      if (((GrReferenceExpression)parent).isQualified()) {
        return false;
      }
      if (parent.getParent() instanceof GrCall) {
        return false;
      }
      ProcessingContext context = new ProcessingContext();
      if (PlatformPatterns.psiElement().inside(GrExpression.class).afterLeaf(
        PlatformPatterns.psiElement().inside(PlatformPatterns.psiElement(GrExpression.class).save("prevExpr"))).accepts(element, context)) {
        PsiElement prevExpr = (PsiElement)context.get("prevExpr");
        if (prevExpr.getTextRange().getEndOffset() <= element.getTextRange().getStartOffset()) {
          return false;
        }
      }
      return true;
    }
  }
  public static class Declaration extends GroovyTemplateContextType {
    public Declaration() {
      super("GROOVY_DECLARATION", "Declaration", Generic.class);
    }

    @Override
    protected boolean isInContext(@NotNull PsiElement element) {
      if (PsiTreeUtil.getParentOfType(element, GrCodeBlock.class, false, GrTypeDefinition.class) != null) {
        return false;
      }

      if (element instanceof PsiComment) {
        return false;
      }

      return GroovyCompletionData.suggestClassInterfaceEnum(element) || GroovyCompletionData.suggestFinalDef(element);
    }
  }


}
