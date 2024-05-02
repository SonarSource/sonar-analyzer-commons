/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2024 SonarSource SA
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
package org.sonarsource.analyzer.commons.checks.verifier.internal;

public class Report {
  private int expectedIssueCount;
  private int actualIssueCount;
  private StringBuilder context;
  private StringBuilder expected;
  private StringBuilder actual;

  private int expectedQuickfixCount;
  private int actualQuickfixCount;
  private StringBuilder quickfixContext;
  private StringBuilder expectedQuickfixes;
  private StringBuilder actualQuickfixes;


  public Report() {
    expectedIssueCount = 0;
    actualIssueCount = 0;
    context = new StringBuilder();
    expected = new StringBuilder();
    actual = new StringBuilder();

    expectedQuickfixCount = 0;
    actualQuickfixCount = 0;
    quickfixContext = new StringBuilder();
    expectedQuickfixes = new StringBuilder();
    actualQuickfixes = new StringBuilder();
  }

  public int getExpectedIssueCount() {
    return expectedIssueCount;
  }

  public void setExpectedIssueCount(int count) {
    expectedIssueCount = count;
  }

  public int getActualIssueCount() {
    return actualIssueCount;
  }

  public void setActualIssueCount(int count) {
    actualIssueCount = count;
  }

  public int getExpectedQuickfixCount() {
    return expectedQuickfixCount;
  }

  public void setExpectedQuickfixCount(int expectedQuickfixCount) {
    this.expectedQuickfixCount = expectedQuickfixCount;
  }

  public int getActualQuickfixCount() {
    return actualQuickfixCount;
  }

  public void setActualQuickfixCount(int actualQuickfixCount) {
    this.actualQuickfixCount = actualQuickfixCount;
  }

  public String getExpectedQuickfixes() {
    return expectedQuickfixes.toString();
  }

  public Report appendExpectedQuickfixes(String expectedQuickfixes) {
    this.expectedQuickfixes.append(expectedQuickfixes);
    return this;
  }

  public String getActualQuickfixes() {
    return actualQuickfixes.toString();
  }

  public Report appendActualQuickfixes(String actualQuickfixes) {
    this.actualQuickfixes.append(actualQuickfixes);
    return this;
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

  public String getQuickfixContext() {
    return quickfixContext.toString();
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

  public Report prependContext(String text) {
    context.insert(0, text);
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

  public Report appendQuickfixContext(String text){
    quickfixContext.append(text);
    return this;
  }

  public Report append(Report report) {
    expectedIssueCount += report.expectedIssueCount;
    actualIssueCount += report.actualIssueCount;
    expectedQuickfixCount += report.expectedQuickfixCount;
    actualQuickfixCount += report.actualQuickfixCount;
    context.append(report.context).append("; ");
    expected.append(report.expected).append("\n");
    actual.append(report.actual).append("\n");
    expectedQuickfixes.append(report.expectedQuickfixes).append("\n");
    actualQuickfixes.append(report.actualQuickfixes).append("\n");
    quickfixContext.append(report.quickfixContext).append("; ");
    return this;
  }

}
