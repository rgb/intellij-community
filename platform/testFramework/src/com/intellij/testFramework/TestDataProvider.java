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
package com.intellij.testFramework;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class TestDataProvider implements DataProvider {
  private final Project myProject;

  public TestDataProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    else if (PlatformDataKeys.EDITOR.is(dataId) || OpenFileDescriptor.NAVIGATE_IN_EDITOR.is(dataId)) {
      return FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    }
    else {
      Editor editor = (Editor)getData(PlatformDataKeys.EDITOR.getName());
      if (editor != null) {
        FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(myProject);
        return manager.getData(dataId, editor, manager.getSelectedFiles()[0]);
      }
      return null;
    }
  }
}
