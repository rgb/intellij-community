/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ui.PathEditor;
import com.intellij.openapi.roots.ui.OrderRootTypeUIFactory;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Rustam Vishnyakov
 */
public class DocumentationOrderRootTypeUIFactory implements OrderRootTypeUIFactory {
  
  private static final Icon ICON = IconLoader.getIcon("/nodes/javaDocFolder.png");

  @Override
  @Nullable
  public PathEditor createPathEditor(Sdk sdk) {
    return null;
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getNodeText() {
    return ProjectBundle.message("library.docs.node");
  }
}