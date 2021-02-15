/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2021 SonarSource SA
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
package com.sonarsource.checks.coverage;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

public final class UtilityClass {

  private UtilityClass() {
    // utility class
  }

  public static void assertGoodPractice(Class<?> utilityClass) throws ReflectiveOperationException {
    if (!Modifier.isFinal(utilityClass.getModifiers())) {
      throw new IllegalStateException("Utility class " + utilityClass.getSimpleName() + " should be 'final'");
    }
    Constructor<?>[] constructors = utilityClass.getDeclaredConstructors();
    if (constructors.length != 1) {
      throw new IllegalStateException("Utility class " + utilityClass.getSimpleName() + " should only have one constructor.");
    }
    Constructor<?> constructor = constructors[0];
    if (!Modifier.isPrivate(constructor.getModifiers())) {
      throw new IllegalStateException(utilityClass.getSimpleName() + " constructor should be private.");
    }
    constructor.setAccessible(true);
    constructor.newInstance();
  }

}
