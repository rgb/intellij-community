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
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.checkout.CheckoutAction;
import com.intellij.ui.UIBundle;

import java.util.Arrays;
import java.util.Comparator;

public class GetFromVcsAction extends WelcomePopupAction{

  protected void fillActions(DefaultActionGroup group) {
    final CheckoutProvider[] providers = Extensions.getExtensions(CheckoutProvider.EXTENSION_POINT_NAME);
    Arrays.sort(providers, new Comparator<CheckoutProvider>() {
      public int compare(final CheckoutProvider o1, final CheckoutProvider o2) {
        // not strict but will do
        return o1.getVcsName().compareTo(o2.getVcsName());
      }
    });
    for (CheckoutProvider provider : providers) {
      group.add(new CheckoutAction(provider));
    }
  }

  protected String getCaption() {
    return UIBundle.message("welcome.screen.get.from.vcs.action.checkout.from.list.popup.title");
  }

  protected String getTextForEmpty() {
    return UIBundle.message("welcome.screen.get.from.vcs.action.no.vcs.plugins.with.check.out.action.installed.action.name");
  }

  @Override
  protected boolean isSilentlyChooseSingleOption() {
    return true;
  }
}
