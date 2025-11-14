/*
 * SonarSource Performance Measure Library
 * Copyright (C) 2009-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.performance.measure.log;

import java.util.function.Supplier;

public class SonarSourceLoggerBridge extends Logger {

  @SuppressWarnings("java:S1312")
  private final org.slf4j.Logger delegate;

  public SonarSourceLoggerBridge(Class<?> cls) {
    delegate = org.slf4j.LoggerFactory.getLogger(cls);
  }

  @Override
  public void debug(Supplier<String> messageSupplier) {
    if (delegate.isDebugEnabled()) {
      delegate.debug(messageSupplier.get());
    }
  }

  @Override
  public void info(Supplier<String> messageSupplier) {
    if (delegate.isInfoEnabled()) {
      delegate.info(messageSupplier.get());
    }
  }

  @Override
  public void warning(Supplier<String> messageSupplier) {
    if (delegate.isWarnEnabled()) {
      delegate.warn(messageSupplier.get());
    }
  }

  @Override
  public void error(Supplier<String> messageSupplier) {
    delegate.error(messageSupplier.get());
  }

}
