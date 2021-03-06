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
package com.intellij.framework;

import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class FrameworkType {
  private final String myId;
  private final String myPresentableName;
  private final Icon myIcon;

  public FrameworkType(@NotNull String id, @NotNull String presentableName, @Nullable Icon icon) {
    myId = id;
    myPresentableName = presentableName;
    myIcon = icon != null ? icon : EmptyIcon.ICON_16;
  }

  @NotNull
  public String getPresentableName() {
    return myPresentableName;
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @NotNull
  public Icon getIcon() {
    return myIcon;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myId.equals(((FrameworkType)o).myId);
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }
}
