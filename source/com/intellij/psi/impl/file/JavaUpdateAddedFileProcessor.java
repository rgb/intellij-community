package com.intellij.psi.impl.file;

import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Maxim.Mossienko
 *         Date: Sep 18, 2008
 *         Time: 3:33:07 PM
 */
public class JavaUpdateAddedFileProcessor extends UpdateAddedFileProcessor {
  public boolean canProcessElement(final PsiFile element) {
    return true;
  }

  @Override
  protected boolean isDefault() {
    return true;
  }

  public void update(final PsiFile element, PsiFile originalElement) throws IncorrectOperationException {
    final TreeElement tree = (TreeElement)SourceTreeToPsiMap.psiElementToTree(element);
    if (tree == null) return;

    ChangeUtil.encodeInformation(tree);
    PsiUtil.updatePackageStatement(element);
    ChangeUtil.decodeInformation((TreeElement)SourceTreeToPsiMap.psiElementToTree(element));
  }
}
