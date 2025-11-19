/*
 * SonarSource Analyzers Recognizers
 * Copyright (C) 2009-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.analyzer.commons.recognizers;

public abstract class Detector {

  private final double probability;

  protected Detector(double probability) {
    if (probability < 0 || probability > 1) {
      throw new IllegalArgumentException("probability should be between [0 .. 1]");
    }
    this.probability = probability;
  }

  public abstract int scan(String line);

  public final double recognition(String line) {
    int matchers = scan(line);
    if (matchers == 0) {
      return 0;
    }
    return 1 - Math.pow(1 - probability, matchers);
  }
}
