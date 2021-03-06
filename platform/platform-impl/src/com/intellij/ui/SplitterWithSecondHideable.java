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
package com.intellij.ui;

import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.changes.RefreshablePanel;
import com.intellij.util.OnOffListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/22/11
 * Time: 2:33 PM
 */
public abstract class SplitterWithSecondHideable {
  private final Splitter mySplitter;
  private final AbstractTitledSeparatorWithIcon myTitledSeparator;
  private final boolean myVertical;
  private final OnOffListener<Integer> myListener;
  private final JPanel myFictivePanel;
  private Splitter.Divider mySuperDivider;
  private float myPreviousProportion;

  public SplitterWithSecondHideable(final boolean vertical,
                                    final String separatorText,
                                    final JComponent firstComponent,
                                    final OnOffListener<Integer> listener) {
    myVertical = vertical;
    myListener = listener;
    myFictivePanel = new JPanel(new BorderLayout());
    Icon icon;
    Icon openIcon;
    if (vertical) {
      icon = IconLoader.getIcon("/general/comboArrow.png");
      openIcon = IconLoader.getIcon("/general/comboUpPassive.png");
    } else {
      icon = IconLoader.getIcon("/general/comboArrowRight.png");
      openIcon = IconLoader.getIcon("/general/comboArrowRightPassive.png");
    }

    myTitledSeparator = new AbstractTitledSeparatorWithIcon(icon, openIcon, separatorText, true, true) {
      @Override
      protected RefreshablePanel createPanel() {
        return createDetails();
      }

      @Override
      protected void onImpl() {
        final int firstSize = vertical ? mySplitter.getFirstComponent().getHeight() : mySplitter.getFirstComponent().getWidth();
        final float proportion = myPreviousProportion > 0 ? myPreviousProportion : getSplitterInitialProportion();
        mySplitter.setProportion(proportion);
        mySplitter.setSecondComponent(myDetailsComponent.getPanel());
        mySplitter.revalidate();
        mySplitter.repaint();
        myListener.on((int) ((1 - proportion) * firstSize / proportion));
        mySuperDivider.setResizeEnabled(true);
      }

      @Override
      protected void offImpl() {
        final int previousSize = vertical ? mySplitter.getSecondComponent().getHeight() : mySplitter.getSecondComponent().getWidth();
        mySplitter.setSecondComponent(myFictivePanel);
        myPreviousProportion = mySplitter.getProportion();
        mySplitter.setProportion(1.0f);
        mySplitter.revalidate();
        mySplitter.repaint();
        myListener.off(previousSize);
        mySuperDivider.setResizeEnabled(false);
      }
    };
    mySplitter = new Splitter(vertical) {
    {
      myTitledSeparator.mySeparator.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
          myTitledSeparator.mySeparator
                  .setCursor(myTitledSeparator.myOn ? new Cursor(Cursor.S_RESIZE_CURSOR) : new Cursor(Cursor.DEFAULT_CURSOR));
          ((MyDivider) mySuperDivider).processMouseEvent(e);
        }

        @Override
        public void mouseExited(MouseEvent e) {
          myTitledSeparator.mySeparator.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
          ((MyDivider) mySuperDivider).processMouseEvent(e);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
          ((MyDivider) mySuperDivider).processMouseEvent(e);
        }

        @Override
        public void mousePressed(MouseEvent e) {
          ((MyDivider) mySuperDivider).processMouseEvent(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
          ((MyDivider) mySuperDivider).processMouseEvent(e);
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
          ((MyDivider) mySuperDivider).processMouseEvent(e);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
          ((MyDivider) mySuperDivider).processMouseEvent(e);
        }

        @Override
        public void mouseMoved(MouseEvent e) {
          ((MyDivider) mySuperDivider).processMouseEvent(e);
        }
      });

      myTitledSeparator.mySeparator.addMouseMotionListener(new MouseMotionListener() {
        @Override
        public void mouseDragged(MouseEvent e) {
          ((MyDivider) mySuperDivider).processMouseMotionEvent(e);
        }

        @Override
        public void mouseMoved(MouseEvent e) {
          ((MyDivider) mySuperDivider).processMouseMotionEvent(e);
        }
      });
    }
        @Override
        protected Divider createDivider() {
        mySuperDivider = new MyDivider();
        mySuperDivider.add(myTitledSeparator,
                           new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                  new Insets(0, 0, 0, 0), 0, 0));
        return mySuperDivider;
      }

        @Override
        public int getDividerWidth() {
        return vertical ? myTitledSeparator.getHeight() : myTitledSeparator.getWidth();
      }

        class MyDivider extends Divider {
          @Override
          public void processMouseMotionEvent(MouseEvent e) {
            super.processMouseMotionEvent(e);
          }

          @Override
          public void processMouseEvent(MouseEvent e) {
            super.processMouseEvent(e);
          }
        }
    };
    mySplitter.setDoubleBuffered(true);
    mySplitter.setFirstComponent(firstComponent);
    mySplitter.setSecondComponent(myFictivePanel);
    mySplitter.setProportion(1.0f);
    mySuperDivider.setResizeEnabled(false);
  }

  public JComponent getComponent() {
    return mySplitter;
  }

  protected abstract RefreshablePanel createDetails();
  protected abstract float getSplitterInitialProportion();

  public float getUsedProportion() {
    return isOn() ? mySplitter.getProportion() : myPreviousProportion;
  }
  
  public void on() {
    myTitledSeparator.on();
  }
  
  public void off() {
    myTitledSeparator.off();
  }

  public boolean isOn() {
    return myTitledSeparator.myOn;
  }
}
