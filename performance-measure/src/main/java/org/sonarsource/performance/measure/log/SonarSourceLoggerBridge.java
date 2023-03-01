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

import java.util.function.Supplier;

public class SonarSourceLoggerBridge extends Logger {

  @SuppressWarnings("java:S1312")
  private final org.sonar.api.utils.log.Logger delegate;

  public SonarSourceLoggerBridge(Class<?> cls) {
    delegate = org.sonar.api.utils.log.Loggers.get(cls);
  }

  @Override
  public void debug(Supplier<String> messageSupplier) {
    delegate.debug(messageSupplier);
  }

  @Override
  public void info(Supplier<String> messageSupplier) {
    delegate.info(messageSupplier.get());
  }

  @Override
  public void warning(Supplier<String> messageSupplier) {
    delegate.warn(messageSupplier.get());
  }

  @Override
  public void error(Supplier<String> messageSupplier) {
    delegate.error(messageSupplier.get());
  }

}
