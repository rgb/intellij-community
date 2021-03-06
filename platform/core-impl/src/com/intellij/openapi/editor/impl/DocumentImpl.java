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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DocumentImpl extends UserDataHolderBase implements DocumentEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.DocumentImpl");

  private final CopyOnWriteArrayList<DocumentListener> myDocumentListeners = ContainerUtil.createEmptyCOWList();
  private final RangeMarkerTree<RangeMarkerEx> myRangeMarkers = new RangeMarkerTree<RangeMarkerEx>(this);
  private final List<RangeMarker> myGuardedBlocks = new ArrayList<RangeMarker>();
  private ReadonlyFragmentModificationHandler myReadonlyFragmentModificationHandler;

  private final LineSet myLineSet = new LineSet();
  private final CharArray myText = new MyCharArray();

  private boolean myIsReadOnly = false;
  private boolean isStripTrailingSpacesEnabled = true;
  private volatile long myModificationStamp;
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

  private DocumentListener[] myCachedDocumentListeners;
  private final List<EditReadOnlyListener> myReadOnlyListeners = ContainerUtil.createEmptyCOWList();

  private int myCheckGuardedBlocks = 0;
  private boolean myGuardsSuppressed = false;
  private boolean myEventsHandling = false;
  private final boolean myAssertWriteAccess;
  private volatile boolean myDoingBulkUpdate = false;
  private boolean myAcceptSlashR = false;
  private boolean myChangeInProgress;

  public DocumentImpl(String text) {
    this((CharSequence)text);
  }

  public DocumentImpl(CharSequence chars) {
    this();
    assertValidSeparators(chars);
    myText.setText(this, chars);
    DocumentEvent event = new DocumentEventImpl(this, 0, null, null, -1, true);
    myLineSet.documentCreated(event);
  }

  private DocumentImpl() {
    this(false);
  }

  public DocumentImpl(boolean forUseInNonAWTThread) {
    setCyclicBufferSize(0);
    setModificationStamp(LocalTimeCounter.currentTime());
    myAssertWriteAccess = !forUseInNonAWTThread;
  }

  public boolean setAcceptSlashR(boolean accept) {
    try {
      return myAcceptSlashR;
    }
    finally {
      myAcceptSlashR = accept;
    }
  }

  public char[] getRawChars() {
    return myText.getChars();
  }

  @NotNull
  public char[] getChars() {
    return CharArrayUtil.fromSequence(getCharsSequence());
  }

  public void setStripTrailingSpacesEnabled(boolean isEnabled) {
    isStripTrailingSpacesEnabled = isEnabled;
  }
  
  @TestOnly
  public boolean stripTrailingSpaces() {
    return stripTrailingSpaces(null, false, false, -1, -1);
  }

  /**
   * @return true if stripping was completed successfully, false if the document prevented stripping by e.g. caret being in the way
   */
  public boolean stripTrailingSpaces(@Nullable final Project project,
                                     boolean inChangedLinesOnly,
                                     boolean virtualSpaceEnabled,
                                     int caretLine,
                                     int caretOffset) {
    if (!isStripTrailingSpacesEnabled) {
      return true;
    }

    boolean markAsNeedsStrippingLater = false;
    CharSequence text = myText.getCharArray();
    RangeMarker caretMarker = caretOffset < 0 || caretOffset > getTextLength() ? null : createRangeMarker(caretOffset, caretOffset);
    try {
      for (int line = 0; line < myLineSet.getLineCount(); line++) {
        if (inChangedLinesOnly && !myLineSet.isModified(line)) continue;
        int whiteSpaceStart = -1;
        final int lineEnd = myLineSet.getLineEnd(line) - myLineSet.getSeparatorLength(line);
        int lineStart = myLineSet.getLineStart(line);
        for (int offset = lineEnd - 1; offset >= lineStart; offset--) {
          char c = text.charAt(offset);
          if (c != ' ' && c != '\t') {
            break;
          }
          whiteSpaceStart = offset;
        }
        if (whiteSpaceStart == -1) continue;
        if (!virtualSpaceEnabled && caretLine == line && caretMarker != null &&
            caretMarker.getStartOffset() >= 0 && whiteSpaceStart < caretMarker.getStartOffset()) {
          // mark this as a document that needs stripping later
          // otherwise the caret would jump madly
          markAsNeedsStrippingLater = true;
        }
        else {
          final int finalStart = whiteSpaceStart;
          ApplicationManager
            .getApplication().runWriteAction(new DocumentRunnable(this, project) {
            public void run() {
              CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
                public void run() {
                  deleteString(finalStart, lineEnd);
                }
              });
            }
          });
          text = myText.getCharArray();
        }
      }
    }
    finally {
      if (caretMarker != null) caretMarker.dispose();
    }
    return markAsNeedsStrippingLater;
  }

  public void setReadOnly(boolean isReadOnly) {
    if (myIsReadOnly != isReadOnly) {
      myIsReadOnly = isReadOnly;
      myPropertyChangeSupport.firePropertyChange(Document.PROP_WRITABLE, !isReadOnly, isReadOnly);
    }
  }

  public ReadonlyFragmentModificationHandler getReadonlyFragmentModificationHandler() {
    return myReadonlyFragmentModificationHandler;
  }

  public void setReadonlyFragmentModificationHandler(final ReadonlyFragmentModificationHandler readonlyFragmentModificationHandler) {
    myReadonlyFragmentModificationHandler = readonlyFragmentModificationHandler;
  }

  public boolean isWritable() {
    return !myIsReadOnly;
  }

  public boolean removeRangeMarker(@NotNull RangeMarkerEx rangeMarker) {
    return myRangeMarkers.removeInterval(rangeMarker);
  }

  public void addRangeMarker(@NotNull RangeMarkerEx rangeMarker, int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    myRangeMarkers.addInterval(rangeMarker, start, end, greedyToLeft, greedyToRight, layer);
  }

  @TestOnly
  public int getRangeMarkersSize() {
    return myRangeMarkers.size();
  }
  @TestOnly
  public int getRangeMarkersNodeSize() {
    return myRangeMarkers.nodeSize();
  }

  @NotNull
  public RangeMarker createGuardedBlock(int startOffset, int endOffset) {
    LOG.assertTrue(startOffset <= endOffset, "Should be startOffset <= endOffset");
    RangeMarker block = createRangeMarker(startOffset, endOffset, true);
    myGuardedBlocks.add(block);
    return block;
  }

  public void removeGuardedBlock(@NotNull RangeMarker block) {
    myGuardedBlocks.remove(block);
  }

  @NotNull
  public List<RangeMarker> getGuardedBlocks() {
    return myGuardedBlocks;
  }

  @SuppressWarnings({"ForLoopReplaceableByForEach"}) // Way too many garbage is produced otherwise in AbstractList.iterator()
  public RangeMarker getOffsetGuard(int offset) {
    for (int i = 0; i < myGuardedBlocks.size(); i++) {
      RangeMarker block = myGuardedBlocks.get(i);
      if (offsetInRange(offset, block.getStartOffset(), block.getEndOffset())) return block;
    }

    return null;
  }

  public RangeMarker getRangeGuard(int start, int end) {
    for (RangeMarker block : myGuardedBlocks) {
      if (rangesIntersect(start, true, block.getStartOffset(), block.isGreedyToLeft(), end, true, block.getEndOffset(), block.isGreedyToRight())) {
        return block;
      }
    }

    return null;
  }

  public void startGuardedBlockChecking() {
    myCheckGuardedBlocks++;
  }

  public void stopGuardedBlockChecking() {
    LOG.assertTrue(myCheckGuardedBlocks > 0, "Unpaired start/stopGuardedBlockChecking");
    myCheckGuardedBlocks--;
  }

  private static boolean offsetInRange(int offset, int start, int end) {
    return start <= offset && offset < end;
  }

  private static boolean rangesIntersect(int start0, boolean leftInclusive0,
                                         int start1, boolean leftInclusive1,
                                         int end0, boolean rightInclusive0,
                                         int end1, boolean rightInclusive1) {
    if (start0 > start1 || start0 == start1 && !leftInclusive0) {
      return rangesIntersect(start1, leftInclusive1, start0, leftInclusive0, end1, rightInclusive1, end0, rightInclusive0);
    }
    if (end0 == start1) return leftInclusive1 && rightInclusive0;
    return end0 > start1;
  }

  @NotNull
  public RangeMarker createRangeMarker(int startOffset, int endOffset) {
    return createRangeMarker(startOffset, endOffset, false);
  }

  @NotNull
  public RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
    if (!(0 <= startOffset && startOffset <= endOffset && endOffset <= getTextLength())) {
      LOG.error("Incorrect offsets: startOffset=" + startOffset + ", endOffset=" + endOffset + ", text length=" + getTextLength());
    }
    return surviveOnExternalChange
           ? new PersistentRangeMarker(this, startOffset, endOffset,true)
           : new RangeMarkerImpl(this, startOffset, endOffset,true);
  }

  public long getModificationStamp() {
    return myModificationStamp;
  }

  public void setModificationStamp(long modificationStamp) {
    myModificationStamp = modificationStamp;
  }

  public void replaceText(@NotNull CharSequence chars, long newModificationStamp) {
    replaceString(0, getTextLength(), chars, newModificationStamp, true); //TODO: optimization!!!
    clearLineModificationFlags();
  }

  public int getListenersCount() {
    return myDocumentListeners.size();
  }

  public void insertString(int offset, @NotNull CharSequence s) {
    if (offset < 0) throw new IndexOutOfBoundsException("Wrong offset: " + offset);
    if (offset > getTextLength()) {
      throw new IndexOutOfBoundsException("Wrong offset: " + offset +"; documentLength: "+getTextLength()+ "; " + s.subSequence(Math.max(0, getTextLength() - 20), getTextLength()));
    }
    assertWriteAccess();
    assertValidSeparators(s);
    assertNotNestedModification();

    if (!isWritable()) throw new ReadOnlyModificationException(this);
    if (s.length() == 0) return;

    RangeMarker marker = getRangeGuard(offset, offset);
    if (marker != null) {
      throwGuardedFragment(marker, offset, null, s.toString());
    }

    myText.insert(this, s, offset);
  }

  public void deleteString(int startOffset, int endOffset) {
    assertBounds(startOffset, endOffset);

    assertWriteAccess();
    if (!isWritable()) throw new ReadOnlyModificationException(this);
    if (startOffset == endOffset) return;
    assertNotNestedModification();

    CharSequence sToDelete = myText.substring(startOffset, endOffset);

    RangeMarker marker = getRangeGuard(startOffset, endOffset);
    if (marker != null) {
      throwGuardedFragment(marker, startOffset, sToDelete.toString(), null);
    }

    myText.remove(this, startOffset, endOffset,sToDelete);
  }

  public void replaceString(int startOffset, int endOffset, @NotNull CharSequence s) {
    replaceString(startOffset, endOffset, s, LocalTimeCounter.currentTime(), startOffset == 0 && endOffset == getTextLength());
  }

  private void replaceString(int startOffset, int endOffset, CharSequence s, final long newModificationStamp, boolean wholeTextReplaced) {
    assertBounds(startOffset, endOffset);

    assertWriteAccess();
    assertValidSeparators(s);

    if (!isWritable()) {
      throw new ReadOnlyModificationException(this);
    }
    assertNotNestedModification();

    final int newStringLength = s.length();
    final CharSequence chars = getCharsSequence();
    int newStartInString = 0;
    int newEndInString = newStringLength;
    while (newStartInString < newStringLength &&
           startOffset < endOffset &&
           s.charAt(newStartInString) == chars.charAt(startOffset)) {
      startOffset++;
      newStartInString++;
    }

    while (endOffset > startOffset &&
           newEndInString > newStartInString &&
           s.charAt(newEndInString - 1) == chars.charAt(endOffset - 1)) {
      newEndInString--;
      endOffset--;
    }
    //if (newEndInString - newStartInString == 0 && startOffset == endOffset) {
    //setModificationStamp(newModificationStamp);
    //return;
    //}

    s = s.subSequence(newStartInString, newEndInString);
    CharSequence sToDelete = myText.substring(startOffset, endOffset);
    RangeMarker guard = getRangeGuard(startOffset, endOffset);
    if (guard != null) {
      throwGuardedFragment(guard, startOffset, sToDelete.toString(), s.toString());
    }

    myText.replace(this, startOffset, endOffset, sToDelete, s, newModificationStamp, wholeTextReplaced);
  }

  private void assertBounds(final int startOffset, final int endOffset) {
    if (startOffset < 0 || startOffset > getTextLength()) {
      throw new IndexOutOfBoundsException("Wrong startOffset: " + startOffset+"; documentLength: "+getTextLength());
    }
    if (endOffset < 0 || endOffset > getTextLength()) {
      throw new IndexOutOfBoundsException("Wrong endOffset: " + endOffset+"; documentLength: "+getTextLength());
    }
    if (endOffset < startOffset) {
      throw new IllegalArgumentException("endOffset < startOffset: " + endOffset + " < " + startOffset+"; documentLength: "+getTextLength());
    }
  }

  private void assertWriteAccess() {
    if (myAssertWriteAccess) {
      final Application application = ApplicationManager.getApplication();
      if (application != null) {
        application.assertWriteAccessAllowed();
      }
    }
  }

  private void assertValidSeparators(final CharSequence s) {
    if (myAcceptSlashR) return;
    StringUtil.assertValidSeparators(s);
  }

  /**
   * All document change actions follows the algorithm below:
   * <pre>
   * <ol>
   *   <li>
   *     All {@link #addDocumentListener(DocumentListener) registered listeners} are notified
   *     {@link DocumentListener#beforeDocumentChange(DocumentEvent) before the change};
   *   </li>
   *   <li>The change is performed </li>
   *   <li>
   *     All {@link #addDocumentListener(DocumentListener) registered listeners} are notified
   *     {@link DocumentListener#documentChanged(DocumentEvent) after the change};
   *   </li>
   * </ol>
   * </pre>
   * <p/>
   * There is a possible case that <code>'before change'</code> notification produces new change. We have a problem then - imagine
   * that initial change was <code>'replace particular range at document end'</code> and <code>'nested change'</code> was to
   * <code>'remove text at document end'</code>. That means that when initial change will be actually performed, the document may be
   * not long enough to contain target range.
   * <p/>
   * Current method allows to check if document change is a <code>'nested call'</code>.
   *
   * @throws IllegalStateException  if this method is called during a <code>'nested document modification'</code>
   */
  private void assertNotNestedModification() throws IllegalStateException {
    if (myChangeInProgress) {
      throw new IllegalStateException("Detected nested request for document modification from 'before change' callback!");
    }
  }

  private void throwGuardedFragment(RangeMarker guard, int offset, String oldString, String newString) {
    if (myCheckGuardedBlocks > 0 && !myGuardsSuppressed) {
      DocumentEvent event = new DocumentEventImpl(this, offset, oldString, newString, myModificationStamp, false);
      throw new ReadOnlyFragmentModificationException(event, guard);
    }
  }

  public void suppressGuardedExceptions() {
    myGuardsSuppressed = true;
  }

  public void unSuppressGuardedExceptions() {
    myGuardsSuppressed = false;
  }

  public boolean isInEventsHandling() {
    return myEventsHandling;
  }

  public void clearLineModificationFlags() {
    myLineSet.clearModificationFlags();
  }

  @NotNull
  private DocumentEvent beforeChangedUpdate(int offset, CharSequence oldString, CharSequence newString, boolean wholeTextReplaced) {
    myChangeInProgress = true;
    try {
      return doBeforeChangedUpdate(offset, oldString, newString, wholeTextReplaced);
    }
    finally {
      myChangeInProgress = false;
    }
  }

  @NotNull
  private DocumentEvent doBeforeChangedUpdate(int offset, CharSequence oldString, CharSequence newString, boolean wholeTextReplaced) {
    DocumentEvent event = new DocumentEventImpl(this, offset, oldString, newString, myModificationStamp, wholeTextReplaced);
    //System.out.printf("%nbefore change: offset=%d, old text='%s', new text='%s'%n document: id=%d, modification stamp=%d%nDocument:'%s'%n",
    //                  event.getOffset(), event.getOldFragment(), event.getNewFragment(), System.identityHashCode(this),
    //                  getModificationStamp(), getText());

    if (!ShutDownTracker.isShutdownHookRunning()) {
      DocumentListener[] listeners = getCachedListeners();
      for (int i = listeners.length - 1; i >= 0; i--) {
        try {
          listeners[i].beforeDocumentChange(event);
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }

    myEventsHandling = true;
    return event;
  }

  private void changedUpdate(DocumentEvent event, long newModificationStamp) {
    //System.out.printf("after change: document id=%d, new modification stamp=%d%ndocument='%s'%n", System.identityHashCode(this),
    //                  getModificationStamp(), getText());
    try {
      if (LOG.isDebugEnabled()) LOG.debug(event.toString());

      myLineSet.changedUpdate(event);
      setModificationStamp(newModificationStamp);

      if (!ShutDownTracker.isShutdownHookRunning()) {
        DocumentListener[] listeners = getCachedListeners();
        for (DocumentListener listener : listeners) {
          try {
            listener.documentChanged(event);
          }
          catch (Throwable e) {
            LOG.error(e);
          }
        }
      }
    }
    finally{
      myEventsHandling = false;
    }
  }

  public String getText() {
    assertReadAccessToDocumentsAllowed();
    return myText.toString();
  }

  @NotNull
  @Override
  public String getText(@NotNull TextRange range) {
    assertReadAccessToDocumentsAllowed();
    return myText.substring(range.getStartOffset(), range.getEndOffset()).toString();
  }

  public int getTextLength() {
    assertReadAccessToDocumentsAllowed();
    return myText.length();
  }

  private static void assertReadAccessToDocumentsAllowed() {
    /*
    final ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    if (application != null) {
      application.assertReadAccessToDocumentsAllowed();
    }
    */
  }

/*
  This method should be used very carefully - only to read the array, and to be sure, that nobody changes
  text, while this array is processed.
  Really it is used only to optimize paint in Editor.
  [Valentin] 25.04.2001: More really, it is used in 61 places in 29 files across the project :-)))
*/

  CharSequence getCharsNoThreadCheck() {
    return myText.getCharArray();
  }

  @NotNull
  public CharSequence getCharsSequence() {
    assertReadAccessToDocumentsAllowed();
    return myText.getCharArray();
  }


  public void addDocumentListener(@NotNull DocumentListener listener) {
    myCachedDocumentListeners = null;
    boolean added = myDocumentListeners.addIfAbsent(listener);
    LOG.assertTrue(added, listener);
  }

  public void addDocumentListener(@NotNull final DocumentListener listener, @NotNull Disposable parentDisposable) {
    addDocumentListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeDocumentListener(listener);
      }
    });
  }

  public void removeDocumentListener(@NotNull DocumentListener listener) {
    myCachedDocumentListeners = null;
    boolean success = myDocumentListeners.remove(listener);
    LOG.assertTrue(success);
  }

  public int getLineNumber(int offset) {
    assertReadAccessToDocumentsAllowed();
    int lineIndex = myLineSet.findLineIndex(offset);
    assert lineIndex >= 0;
    return lineIndex;
  }

  @NotNull
  public LineIterator createLineIterator() {
    return myLineSet.createIterator();
  }

  public final int getLineStartOffset(int line) {
    assertReadAccessToDocumentsAllowed();
    if (line == 0) return 0; // otherwise it crashed for zero-length document
    int lineStart = myLineSet.getLineStart(line);
    assert lineStart >= 0;
    return lineStart;
  }

  public final int getLineEndOffset(int line) {
    if (getTextLength() == 0 && line == 0) return 0;
    int result = myLineSet.getLineEnd(line) - getLineSeparatorLength(line);
    assert result >= 0;
    return result;
  }

  public final int getLineSeparatorLength(int line) {
    int separatorLength = myLineSet.getSeparatorLength(line);
    assert separatorLength >= 0;
    return separatorLength;
  }

  public final int getLineCount() {
    int lineCount = myLineSet.getLineCount();
    assert lineCount >= 0;
    return lineCount;
  }

  @NotNull
  private DocumentListener[] getCachedListeners() {
    DocumentListener[] cachedListeners = myCachedDocumentListeners;
    if (cachedListeners == null) {
      DocumentListener[] listeners = myDocumentListeners.toArray(new DocumentListener[myDocumentListeners.size()]);
      Arrays.sort(listeners, PrioritizedDocumentListener.COMPARATOR);
      myCachedDocumentListeners = cachedListeners = listeners;
    }

    return cachedListeners;
  }

  public void fireReadOnlyModificationAttempt() {
    for (EditReadOnlyListener listener : myReadOnlyListeners) {
      listener.readOnlyModificationAttempt(this);
    }
  }

  public void addEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
    myReadOnlyListeners.add(listener);
  }

  public void removeEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
    myReadOnlyListeners.remove(listener);
  }


  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  public void setCyclicBufferSize(int bufferSize) {
    myText.setBufferSize(bufferSize);
  }

  public void setText(@NotNull final CharSequence text) {
    Runnable runnable = new Runnable() {
      public void run() {
        replaceString(0, getTextLength(), text, LocalTimeCounter.currentTime(), true);
      }
    };
    if (CommandProcessor.getInstance().isUndoTransparentActionInProgress()) {
      runnable.run();
    }
    else {
      CommandProcessor.getInstance().executeCommand(null, runnable, "", DocCommandGroupId.noneGroupId(this));
    }

    clearLineModificationFlags();
  }

  @NotNull
  public RangeMarker createRangeMarker(@NotNull final TextRange textRange) {
    return createRangeMarker(textRange.getStartOffset(), textRange.getEndOffset());
  }

  public final boolean isInBulkUpdate() {
    return myDoingBulkUpdate;
  }

  public final void setInBulkUpdate(boolean value) {
    myDoingBulkUpdate = value;
    myText.setDeferredChangeMode(value);
    if (value) {
      getPublisher().updateStarted(this);
    }
    else {
      getPublisher().updateFinished(this);
    }
  }

  private static class DocumentBulkUpdateListenerHolder {
    private static final DocumentBulkUpdateListener ourBulkChangePublisher =
        ApplicationManager.getApplication().getMessageBus().syncPublisher(DocumentBulkUpdateListener.TOPIC);
  }

  private static DocumentBulkUpdateListener getPublisher() {
    return DocumentBulkUpdateListenerHolder.ourBulkChangePublisher;
  }

  public boolean processRangeMarkers(@NotNull Processor<RangeMarker> processor) {
    return myRangeMarkers.process(processor);
  }

  public boolean processRangeMarkersOverlappingWith(int start, int end, @NotNull Processor<RangeMarker> processor) {
    return myRangeMarkers.processOverlappingWith(start, end, processor);
  }

  private static class MyCharArray extends CharArray {
    public MyCharArray() {
      super(0);
    }

    @NotNull
    protected DocumentEvent beforeChangedUpdate(DocumentImpl subj,
                                                int offset,
                                                CharSequence oldString,
                                                CharSequence newString,
                                                boolean wholeTextReplaced) {
      return subj.beforeChangedUpdate(offset, oldString, newString, wholeTextReplaced);
    }

    protected void afterChangedUpdate(@NotNull DocumentEvent event, long newModificationStamp) {
      ((DocumentImpl)event.getDocument()).changedUpdate(event, newModificationStamp);
    }
  }
}

