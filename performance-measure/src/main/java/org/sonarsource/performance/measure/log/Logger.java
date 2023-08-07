/*
 * SonarSource Performance Measure Library
 * Copyright (C) 2009-2023 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.performance.measure.log;

import java.util.function.Function;
import java.util.function.Supplier;

public abstract class Logger {

  public static final String DEFAULT_SLF4J_LOGGER = "org.slf4j.LoggerFactory";

  private static String loggerImpl = DEFAULT_SLF4J_LOGGER;

  public static final Function<Class<?>, Logger> DEFAULT_FACTORY = cls -> {
    try {
      Class.forName(loggerImpl);
      return new SonarSourceLoggerBridge(cls);
    } catch (ClassNotFoundException e) {
      // slf4j logger not available
      return new JavaLoggerBridge(cls);
    }
  };

  private static Function<Class<?>, Logger> factory = DEFAULT_FACTORY;

  public abstract void debug(Supplier<String> message);

  public abstract void info(Supplier<String> message);

  public abstract void warning(Supplier<String> message);

  public abstract void error(Supplier<String> message);

  public static Logger get(Class<?> cls) {
    return factory.apply(cls);
  }

  // Visible for testing
  public static void overrideFactory(Function<Class<?>, Logger> factory, String sonarLoggerClass) {
    Logger.factory = factory;
    Logger.loggerImpl = sonarLoggerClass;
  }

}
