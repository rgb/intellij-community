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
package com.intellij.ide.navigationToolbar;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

/**
* @author Konstantin Bulenkov
*/
class NavBarItem extends SimpleColoredComponent implements Disposable {
  private final String myText;
  private final SimpleTextAttributes myAttributes;
  private final int myIndex;
  private final Icon myIcon;
  private final NavBarPanel myPanel;
  private Object myObject;
  private final boolean isPopupElement;

  public NavBarItem(NavBarPanel panel, Object object, int idx) {
    myPanel = panel;
    myObject = object;
    myIndex = idx;
    isPopupElement = idx == -1;
    if (object != null) {
      Icon closedIcon = NavBarPresentation.getIcon(object, false);
      Icon openIcon = NavBarPresentation.getIcon(object, true);

      if (closedIcon == null && openIcon != null) closedIcon = openIcon;
      if (openIcon == null && closedIcon != null) openIcon = closedIcon;
      if (openIcon == null) {
        openIcon = closedIcon = EmptyIcon.create(5);
      }
      final NavBarPresentation presentation = myPanel.getPresentation();
      myText = NavBarPresentation.getPresentableText(object, myPanel.getWindow());
      myIcon = wrapIcon(openIcon, closedIcon, idx);
      myAttributes = presentation.getTextAttributes(object, false);
    } else {
      myText = "Sample";
      myIcon = PlatformIcons.DIRECTORY_OPEN_ICON;
      myAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }
    Disposer.register(panel, this);

    setOpaque(false);
    setFont(UIUtil.isUnderAquaLookAndFeel() ? UIUtil.getLabelFont().deriveFont(11.0f) : getFont());
    if (isPopupElement || !NavBarPanel.isDecorated()) {
      setIpad(new Insets(1, 2, 1, 2));
    } else {
      setMyBorder(null);
      setBorder(null);
    }
    update();
  }

  /**
   * item for node popup
   * @param panel
   * @param object
   */
  public NavBarItem(NavBarPanel panel, Object object) {
    this(panel, object, -1);
  }

  public Object getObject() {
    return myObject;
  }

  public SimpleTextAttributes getAttributes() {
    return myAttributes;
  }

  public String getText() {
    return myText;
  }

  void update() {
    clear();

    setIcon(myIcon);
    final boolean focused = isFocused();

    final NavBarModel model = myPanel.getModel();
    final boolean selected = isSelected();

    if (!NavBarPanel.isDecorated()) {
      setPaintFocusBorder(selected && !isPopupElement && myPanel.isNodePopupActive());
    }
    setFocusBorderAroundIcon(false);

    setBackground(selected && focused
                  ? UIUtil.getListSelectionBackground()
                  : (UIUtil.isUnderGTKLookAndFeel() ? Color.WHITE : UIUtil.getListBackground()));

    final Color fg = selected && focused
                     ? UIUtil.getListSelectionForeground()
                     : model.getSelectedIndex() < myIndex && model.getSelectedIndex() != -1
                       ? UIUtil.getInactiveTextColor()
                       : myAttributes.getFgColor();

    final Color bg = selected && focused ? UIUtil.getListSelectionBackground() : myAttributes.getBgColor();
    append(myText, new SimpleTextAttributes(bg, fg, myAttributes.getWaveColor(), myAttributes.getStyle()));

    repaint();
  }

  @Override
  protected void doPaint(Graphics2D g) {
    if (isPopupElement || !NavBarPanel.isDecorated()) {
      super.doPaint(g);
    } else {
      doPaintDecorated(g);
    }
  }

  private void doPaintDecorated(Graphics2D g) {
    setPaintFocusBorder(false);
    setIpad(new Insets(0, 0, 0, 0));
    Icon icon = myIcon;
    final Color bg = getBackground();
    g.setPaint(bg);
    int w = getWidth();
    int h = getHeight();
    g.fillRect(0, 0, w, h);
    icon.paintIcon(this, g, 3, (h - icon.getIconHeight()) / 2);
    int x = doPaintText(g, icon.getIconWidth() + 6, false);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    Path2D.Double p = new Path2D.Double();
    g.translate(x, 0);
    p.moveTo(0, 0);                             //
    p.lineTo(10 , h / 2);                       // |\
    p.lineTo(0, h);                             // |/
    p.lineTo(0, 0);
    g.setPaint(getBackground());
    g.fill(p);

    if (isLastElement()) {
      Path2D.Double path = new Path2D.Double();
      path.moveTo(0, 0);
      path.lineTo(10, h / 2);                  // ___
      path.lineTo(0, h);                       // \ |
      path.lineTo(12, h);                      // /_|
      path.lineTo(12, 0);
      path.lineTo(0, 0);
      g.setPaint(isNextSelected() ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground());
      g.fill(path);

      path = new Path2D.Double();
      path.moveTo(0, 0);                       //
      path.lineTo(10, h / 2);                  //   \
      path.lineTo(0, h);                       //   /
      g.setPaint(Color.GRAY);
      g.draw(path);
    }
  }

  private boolean isLastElement() {
    return myIndex != myPanel.getModel().size() - 1;
  }

  @Override
  public Dimension getPreferredSize() {
    final Dimension size = super.getPreferredSize();
    if (! isPopupElement && NavBarPanel.isDecorated()) {
      size.width += 10 + 2*3;
      size.height += 6;
    }
    return size;
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  private boolean isFocused() {
    final Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return isPopupElement || (focusOwner == myPanel && !myPanel.isNodePopupShowing());
  }

  public boolean isSelected() {
    final NavBarModel model = myPanel.getModel();
    return isPopupElement ? myPanel.isSelectedInPopup(myObject) : model.getSelectedIndex() == myIndex;
  }

  @Override
  protected boolean shouldDrawBackground() {
    return isSelected() && isFocused();
  }

  @Override
  protected boolean shouldDrawMacShadow() {
    return UIUtil.isUnderAquaLookAndFeel() && !isSelected();
  }

  @Override
  public boolean isIconOpaque() {
    return false;
  }

  private Icon wrapIcon(final Icon openIcon, final Icon closedIcon, final int idx) {
    return new Icon() {
      public void paintIcon(Component c, Graphics g, int x, int y) {
        if (myPanel.getModel().getSelectedIndex() == idx && myPanel.isNodePopupActive()) {
          openIcon.paintIcon(c, g, x, y);
        }
        else {
          closedIcon.paintIcon(c, g, x, y);
        }
      }

      public int getIconWidth() {
        return openIcon.getIconWidth();
      }

      public int getIconHeight() {
        return openIcon.getIconHeight();
      }
    };
  }

  @Override
  public void dispose() {
  }
    

  private boolean isNextSelected() {
    return myIndex == myPanel.getModel().getSelectedIndex() - 1;
  }
}
