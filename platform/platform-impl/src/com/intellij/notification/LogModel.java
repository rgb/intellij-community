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
package com.intellij.notification;

import com.intellij.notification.impl.NotificationsConfiguration;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class LogModel implements Disposable {
  private final List<Notification> myNotifications = new ArrayList<Notification>();
  private Notification myStatusMessage;
  private final Project myProject;
  final Map<Notification, Runnable> removeHandlers = new THashMap<Notification, Runnable>();

  LogModel(@Nullable Project project, @NotNull Disposable parentDisposable) {
    myProject = project;
    Disposer.register(parentDisposable, this);
  }

  void addNotification(Notification notification) {
    NotificationDisplayType type = NotificationsConfiguration.getSettings(notification.getGroupId()).getDisplayType();
    if (notification.isImportant() || (type != NotificationDisplayType.NONE && type != NotificationDisplayType.TOOL_WINDOW)) {
      synchronized (myNotifications) {
        myNotifications.add(notification);
      }
    }
    setStatusMessage(notification);
  }

  List<Notification> takeNotifications() {
    synchronized (myNotifications) {
      final ArrayList<Notification> result = getNotifications();
      myNotifications.clear();
      return result;
    }
  }

  void setStatusMessage(@Nullable Notification statusMessage) {
    synchronized (myNotifications) {
      if (myStatusMessage == statusMessage) return;

      myStatusMessage = statusMessage;
    }
    StatusBar.Info.set("", myProject, EventLog.LOG_REQUESTOR);
  }

  Notification getStatusMessage() {
    synchronized (myNotifications) {
      return myStatusMessage;
    }
  }

  public void logShown() {
    for (Notification notification : getNotifications()) {
      if (!notification.isImportant()) {
        removeNotification(notification);
      }
    }
    setStatusToImportant();
  }

  public ArrayList<Notification> getNotifications() {
    synchronized (myNotifications) {
      return new ArrayList<Notification>(myNotifications);
    }
  }

  void removeNotification(Notification notification) {
    NotificationsManagerImpl.getNotificationsManagerImpl().remove(notification);
    synchronized (myNotifications) {
      myNotifications.remove(notification);
    }

    Runnable handler = removeHandlers.remove(notification);
    if (handler != null) {
      handler.run();
    }

    if (notification == getStatusMessage()) {
      setStatusToImportant();
    }
  }

  private void setStatusToImportant() {
    ArrayList<Notification> notifications = getNotifications();
    Collections.reverse(notifications);
    setStatusMessage(ContainerUtil.find(notifications, new Condition<Notification>() {
      @Override
      public boolean value(Notification notification) {
        return notification.isImportant();
      }
    }));
  }

  public Project getProject() {
    return myProject;
  }

  @Override
  public void dispose() {
  }
}
