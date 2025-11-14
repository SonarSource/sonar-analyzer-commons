/*
 * SonarSource Analyzers Test Commons
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
    quickfixContext.append(report.quickfixContext);
    return this;
  }

}
