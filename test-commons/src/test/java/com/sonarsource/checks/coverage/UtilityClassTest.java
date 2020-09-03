/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2020 SonarSource SA
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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class UtilityClassTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void self_check() throws Exception {
    UtilityClass.assertGoodPractice(UtilityClass.class);
  }

  @Test
  public void error_if_not_final() throws Exception {
    class Utils { }
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Utility class Utils should be 'final'");
    UtilityClass.assertGoodPractice(Utils.class);
  }

  @Test
  public void error_if_several_constructors() throws Exception {
    final class Utils { private Utils() {} private Utils(int a) {} }
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Utility class Utils should only have one constructor.");
    UtilityClass.assertGoodPractice(Utils.class);
  }

  @Test
  public void error_if_constructor_not_private() throws Exception {
    final class Utils { public Utils() {} }
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Utils constructor should be private.");
    UtilityClass.assertGoodPractice(Utils.class);
  }

}
