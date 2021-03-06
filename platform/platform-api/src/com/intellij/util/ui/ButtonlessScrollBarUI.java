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

/*
 * @author max
 */
package com.intellij.util.ui;

import com.intellij.openapi.util.Disposer;
import com.intellij.ui.LightColors;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;

public class ButtonlessScrollBarUI extends BasicScrollBarUI {
  public static final Color GRADIENT_LIGHT = new Color(0xfb,0xfb,0xfb);
  public static final Color GRADIENT_DARK = new Color(0xd7,0xd7,0xd7);
  public static final Color GRADIENT_THUMB_BORDER = new Color(0xc9,0xc9,0xc9);
  public static final Color TRACK_BACKGROUND = LightColors.SLIGHTLY_GRAY;
  public static final Color TRACK_BORDER = new Color(230,230,230);

  private final AdjustmentListener myAdjustmentListener;
  private final MouseMotionAdapter myMouseMotionListener;
  private final MouseAdapter myMouseListener;

  private final Animator myAnimator;

  private int myAnimationColorShift = 0;
  private boolean myMouseIsOverThumb = false;

  protected ButtonlessScrollBarUI() {
    myAdjustmentListener = new AdjustmentListener() {
      @Override
      public void adjustmentValueChanged(AdjustmentEvent e) {
        resetAnimator();
      }
    };

    final int delayFrames = 4;
    final int framesCount = 10 + delayFrames;
    myAnimator = new Animator("Adjustment fadeout", framesCount, framesCount * 50, false) {
      @Override
      public void paintNow(float frame, float totalFrames, float cycle) {
        myAnimationColorShift = 40;
        if (frame > delayFrames) {
          myAnimationColorShift *= 1 - (frame - delayFrames) / (totalFrames - delayFrames);
        }

        if (scrollbar != null) {
          scrollbar.repaint(((ButtonlessScrollBarUI)scrollbar.getUI()).getThumbBounds());
        }
      }
    };

    myMouseMotionListener = new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        boolean inside = isOverThumb(e.getPoint());
        if (inside != myMouseIsOverThumb) {
          myMouseIsOverThumb = inside;
          resetAnimator();
        }
      }
    };

    myMouseListener = new MouseAdapter() {
      @Override
      public void mouseExited(MouseEvent e) {
        if (myMouseIsOverThumb) {
          myMouseIsOverThumb = false;
          resetAnimator();
        }
      }
    };
  }

  public int getDecrButtonHeight() {
    return decrButton.getHeight();
  }
  public int getIncrButtonHeight() {
    return incrButton.getHeight();
  }

  private void resetAnimator() {
    myAnimator.reset();
    if (scrollbar != null && scrollbar.getValueIsAdjusting() || myMouseIsOverThumb) {
      myAnimator.suspend();
      myAnimationColorShift = 40;
    }
    else {
      myAnimator.resume();
    }
  }

  public static BasicScrollBarUI createNormal() {
    return new ButtonlessScrollBarUI();
  }

  @Override
  public void installUI(JComponent c) {
    super.installUI(c);
    scrollbar.setFocusable(false);
  }

  @Override
  protected void installListeners() {
    super.installListeners();
    scrollbar.addAdjustmentListener(myAdjustmentListener);
    scrollbar.addMouseListener(myMouseListener);
    scrollbar.addMouseMotionListener(myMouseMotionListener);
  }

  private boolean isOverThumb(Point p) {
    final Rectangle bounds = getThumbBounds();
    return bounds != null && bounds.contains(p);
  }

  @Override
  protected void uninstallListeners() {
    super.uninstallListeners();
    scrollbar.removeAdjustmentListener(myAdjustmentListener);
    Disposer.dispose(myAnimator);
  }

  @Override
  protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
    g.setColor(TRACK_BACKGROUND);
    g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

    g.setColor(TRACK_BORDER);
    if (isVertical()) {
      g.drawLine(bounds.x, bounds.y, bounds.x, bounds.y + bounds.height);
    }
    else {
      g.drawLine(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y);
    }
  }

  @Override
  protected Dimension getMinimumThumbSize() {
    final int thickness = getThickness();
    return isVertical() ? new Dimension(thickness, thickness * 2) : new Dimension(thickness * 2, thickness);
  }

  protected int getThickness() {
    return 13;
  }

  @Override
  public Dimension getMaximumSize(JComponent c) {
    int thickness = getThickness();
    return new Dimension(thickness, thickness);
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return getMaximumSize(c);
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return getMaximumSize(c);
  }

  @Override
  protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
    if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
      return;
    }

    g.translate(thumbBounds.x, thumbBounds.y);
    paintMaxiThumb(g, thumbBounds);
    g.translate(-thumbBounds.x, -thumbBounds.y);
  }

  private void paintMaxiThumb(Graphics g, Rectangle thumbBounds) {
    final boolean vertical = isVertical();
    int hgap = vertical ? 2 : 1;
    int vgap = vertical ? 1 : 2;


    int w = adjustThumbWidth(thumbBounds.width - hgap * 2);
    int h = thumbBounds.height - vgap * 2;

    // leave one pixel between thumb and right or bottom edge
    if (vertical) {
      h -= 1;
    }
    else {
      w -= 1;
    }
    final GradientPaint paint;
    final Color start = adjustColor(GRADIENT_LIGHT);
    final Color end = adjustColor(GRADIENT_DARK);

    if (vertical) {
      paint = new GradientPaint(1, 0, start, w + 1, 0, end);
    }
    else {
      paint = new GradientPaint(0, 1, start, 0, h + 1, end);
    }

    ((Graphics2D)g).setPaint(paint);
    g.fillRect(hgap + 1, vgap + 1, w - 1, h - 1);

    g.setColor(GRADIENT_THUMB_BORDER);
    g.drawRoundRect(hgap, vgap, w, h, 3, 3);
  }

  @Override
  public boolean getSupportsAbsolutePositioning() {
    return true;
  }

  protected int adjustThumbWidth(int width) {
    return width;
  }

  protected Color adjustColor(Color c) {
    if (myAnimationColorShift == 0) return c;
    return new SameColor(c.getRed() - myAnimationColorShift);
  }

  private boolean isVertical() {
    return scrollbar.getOrientation() == Adjustable.VERTICAL;
  }

  @Override
  protected JButton createIncreaseButton(int orientation) {
    return new EmptyButton();
  }

  @Override
  protected JButton createDecreaseButton(int orientation) {
    return new EmptyButton();
  }

  private static class EmptyButton extends JButton {
    private EmptyButton() {
      setFocusable(false);
      setRequestFocusEnabled(false);
    }

    @Override
    public Dimension getMaximumSize() {
      return new Dimension(0, 0);
    }

    @Override
    public Dimension getPreferredSize() {
      return getMaximumSize();
    }

    @Override
    public Dimension getMinimumSize() {
      return getMaximumSize();
    }
  }
}
