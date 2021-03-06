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
package org.jetbrains.jpsservice;

import com.intellij.openapi.application.PathManager;
import org.jetbrains.jps.server.Facade;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/12/11
 */
public class Bootstrap {

  public static List<File> buildServerProcessClasspath() {
    final List<File> cp = new ArrayList<File>();
    cp.add(getResourcePath(Server.class));
    cp.add(getResourcePath(com.google.protobuf.Message.class));
    cp.add(getResourcePath(org.jboss.netty.bootstrap.Bootstrap.class));
    final File jpsJar = getResourcePath(Facade.class);
    final File parentFile = jpsJar.getParentFile();
    final File[] files = parentFile.listFiles();
    if (files != null) {
      for (File file : files) {
        final String name = file.getName();
        final boolean shouldAdd =
          name.endsWith("jar") &&
          (name.startsWith("ant") ||
           name.startsWith("jps") ||
           name.startsWith("asm") ||
           name.startsWith("gant")||
           name.startsWith("groovy") ||
           name.startsWith("javac2")
          );
        if (shouldAdd) {
          cp.add(file);
        }
      }
    }
    return cp;
  }

  private static File getResourcePath(Class aClass) {
    return new File(PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class"));
  }

}
