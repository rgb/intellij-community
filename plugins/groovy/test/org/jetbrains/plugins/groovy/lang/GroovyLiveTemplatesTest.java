/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.impl.actions.ListTemplatesAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.IOException;

/**
 * @author peter
 */
public class GroovyLiveTemplatesTest extends LightCodeInsightFixtureTestCase{
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "liveTemplates/";
  }

  public void testJavaTemplatesWorkInGroovyContext() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    expandTemplate(myFixture.getEditor());
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public static void expandTemplate(final Editor editor) {
    new WriteCommandAction(editor.getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        new ListTemplatesAction().actionPerformedImpl(editor.getProject(), editor);
        ((LookupImpl)LookupManager.getActiveLookup(editor)).finishLookup(Lookup.NORMAL_SELECT_CHAR);
      }
    }.execute();
  }

  public void testGroovyStatementContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("inst", "other");
    assertFalse(isApplicable("class Foo {{ if (a <caret>inst) }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>inst }}", template));
    assertTrue(isApplicable("<caret>inst", template));
    assertFalse(isApplicable("class Foo {{ return (<caret>inst) }}", template));
    assertFalse(isApplicable("class Foo {{ return a <caret>inst) }}", template));
  }

  public void testGroovyExpressionContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("lst", "other");
    assertFalse(isApplicable("class Foo {{ if (a <caret>toar) }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>xxx }}", template));
    assertTrue(isApplicable("<caret>xxx", template));
    assertTrue(isApplicable("class Foo {{ return (<caret>aaa) }}", template));
    assertFalse(isApplicable("class Foo {{ return (xxx <caret>yyy) }}", template));
  }

  public void testGroovyDeclarationContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("psvm", "other");
    assertFalse(isApplicable("class Foo {{ <caret>xxx }}", template));
    assertFalse(isApplicable("class Foo {{ <caret>xxx }}", template));
    assertFalse(isApplicable("class Foo {{ if (a <caret>xxx) }}", template));
    assertFalse(isApplicable("class Foo {{ return (<caret>xxx) }}", template));
    assertTrue(isApplicable("class Foo { <caret>xxx }", template));
    assertFalse(isApplicable("class Foo { int <caret>xxx }", template));
    assertFalse(isApplicable("class Foo { /* <caret>xxx */ }", template));
    assertTrue(isApplicable("class Foo {}\n<caret>xxx", template));
    assertTrue(isApplicable("<caret>xxx", template));

    assertTrue(isApplicable("class Foo { void foo(<caret>xxx) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(<caret>xxx String bar ) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(<caret>xxx String bar, int goo ) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(String bar, <caret>xxx int goo ) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(String bar, <caret>xxx goo ) {} }", template));
  }

  private boolean isApplicable(String text, TemplateImpl inst) throws IOException {
    myFixture.configureByText("a.groovy", text);
    return TemplateManagerImpl.isApplicable(myFixture.getFile(), myFixture.getEditor().getCaretModel().getOffset(), inst);
  }

}
