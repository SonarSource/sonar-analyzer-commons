/*
 * SonarQube Analyzer Test Commons
 * Copyright (C) 2009-2017 SonarSource SA
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
package com.sonarsource.checks.verifier.internal;

public class Report {
  private int expectedCount;
  private int actualCount;
  private StringBuilder context;
  private StringBuilder expected;
  private StringBuilder actual;

  public Report() {
    expectedCount = 0;
    actualCount = 0;
    context = new StringBuilder();
    expected = new StringBuilder();
    actual = new StringBuilder();
  }

  public int getExpectedCount() {
    return expectedCount;
  }

  public void setExpectedCount(int count) {
    expectedCount = count;
  }

  public int getActualCount() {
    return actualCount;
  }

  public void setActualCount(int count) {
    actualCount = count;
  }

  public String getActual() {
    return actual.toString();
  }

  public String getExpected() {
    return expected.toString();
  }

  public String getContext() {
    return context.toString();
  }

  public Report appendActual(String text) {
    actual.append(text);
    return this;
  }

  public Report prependActual(String text) {
    actual.insert(0, text);
    return this;
  }

  public Report appendExpected(String text) {
    expected.append(text);
    return this;
  }

  public Report prependExpected(String text) {
    expected.insert(0, text);
    return this;
  }

  public Report appendContext(String text) {
    context.append(text);
    return this;
  }

  public Report append(Report report) {
    expectedCount += report.expectedCount;
    actualCount += report.actualCount;
    context.append(report.context).append("; ");
    expected.append(report.expected).append("\n");
    actual.append(report.actual).append("\n");
    return this;
  }

}
