/*
 * SonarSource Performance Measure Library
 * Copyright (C) 2009-2022 SonarSource SA
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
import java.util.logging.Level;

public class JavaLoggerBridge extends Logger {

  @SuppressWarnings("java:S1312")
  private final java.util.logging.Logger delegate;

  public JavaLoggerBridge(Class<?> cls) {
    delegate = java.util.logging.Logger.getLogger(cls.getName());
  }

  @Override
  public void debug(Supplier<String> messageSupplier) {
    delegate.log(Level.FINE, messageSupplier);
  }

  @Override
  public void info(Supplier<String> messageSupplier) {
    delegate.log(Level.INFO, messageSupplier);
  }

  @Override
  public void warning(Supplier<String> messageSupplier) {
    delegate.log(Level.WARNING, messageSupplier);
  }

  @Override
  public void error(Supplier<String> messageSupplier) {
    delegate.log(Level.SEVERE, messageSupplier);
  }

}
