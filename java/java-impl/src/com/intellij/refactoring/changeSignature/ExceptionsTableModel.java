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
package com.intellij.refactoring.changeSignature;

import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.ui.EditableModel;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class ExceptionsTableModel extends AbstractTableModel implements EditableModel {
  private List<PsiTypeCodeFragment> myTypeCodeFragments;
  private final PsiElement myContext;
  private List<ThrownExceptionInfo> myExceptionInfos;

  public ExceptionsTableModel(PsiElement context) {
    myContext = context;
  }

  public ThrownExceptionInfo[] getThrownExceptions() {
    return myExceptionInfos.toArray(new ThrownExceptionInfo[myExceptionInfos.size()]);
  }

  public void addRow() {
    myExceptionInfos.add(new JavaThrownExceptionInfo());
    myTypeCodeFragments.add(createParameterTypeCodeFragment("", myContext));
    fireTableRowsInserted(myTypeCodeFragments.size() - 1, myTypeCodeFragments.size() - 1);
  }

  public void removeRow(int index) {
    myExceptionInfos.remove(index);
    myTypeCodeFragments.remove(index);
    fireTableRowsDeleted(index, index);
  }

  public void exchangeRows(int index1, int index2) {
    Collections.swap(myExceptionInfos, index1, index2);
    Collections.swap(myTypeCodeFragments, index1, index2);
    if (index1 < index2) {
      fireTableRowsUpdated(index1, index2);
    }
    else {
      fireTableRowsUpdated(index2, index1);
    }
  }

  public int getRowCount() {
    return myTypeCodeFragments.size();
  }

  public int getColumnCount() {
    return 1;
  }

  public Object getValueAt(int rowIndex, int columnIndex) {
    if (columnIndex == 0) {
      return myTypeCodeFragments.get(rowIndex);
    }

    throw new IllegalArgumentException();
  }

  public String getColumnName(int column) {
    switch (column) {
      case 0:
        return RefactoringBundle.message("column.name.type");
      default:
        throw new IllegalArgumentException();
    }
  }

  public boolean isCellEditable(int rowIndex, int columnIndex) {
    switch (columnIndex) {
      case 0:
        return true;

      default:
        throw new IllegalArgumentException();
    }
  }

  public void setTypeInfos(PsiMethod method) {
    PsiClassType[] referencedTypes = method.getThrowsList().getReferencedTypes();
    myTypeCodeFragments = new ArrayList<PsiTypeCodeFragment>(referencedTypes.length);
    myExceptionInfos = new ArrayList<ThrownExceptionInfo>(referencedTypes.length);
    for (int i = 0; i < referencedTypes.length; i++) {
      CanonicalTypes.Type typeWrapper = CanonicalTypes.createTypeWrapper(referencedTypes[i]);
      final PsiTypeCodeFragment typeCodeFragment = createParameterTypeCodeFragment(typeWrapper.getTypeText(), method.getThrowsList());
      typeWrapper.addImportsTo(typeCodeFragment);
      myTypeCodeFragments.add(typeCodeFragment);
      myExceptionInfos.add(new JavaThrownExceptionInfo(i, referencedTypes[i]));
    }
  }

  public PsiTypeCodeFragment createParameterTypeCodeFragment(final String typeText, PsiElement context) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myContext.getProject()).getElementFactory();
    return elementFactory.createTypeCodeFragment(typeText, context, true, PsiElementFactory.ALLOW_ELLIPSIS);
  }

  public PsiTypeCodeFragment[] getTypeCodeFragments() {
    return myTypeCodeFragments.toArray(new PsiTypeCodeFragment[myTypeCodeFragments.size()]);
  }

  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    super.setValueAt(aValue, rowIndex, columnIndex);
    fireTableCellUpdated(rowIndex, columnIndex);
  }
}
