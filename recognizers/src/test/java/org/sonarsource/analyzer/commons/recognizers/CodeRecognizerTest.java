/*
 * SonarSource Analyzers Recognizers
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
package org.sonarsource.analyzer.commons.recognizers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CodeRecognizerTest {

  private static class FakeFootprint implements LanguageFootprint {

    Set<Detector> detectors = new HashSet<>();

    public FakeFootprint() {
      detectors.add(new EndWithDetector(0.95, '}', ';', '{'));
      detectors.add(new KeywordsDetector(0.3, "public", "abstract", "class", "implements", "extends", "return", "throw",
        "private", "protected", "enum", "continue", "assert", "package", "synchronized", "boolean", "this", "double", "instanceof",
        "final", "interface", "static", "void", "long", "int", "float", "super", "true", "case:"));
    }

    @Override
    public Set<Detector> getDetectors() {
      return detectors;
    }
  }

  @Test
  public void isLineOfCode() {
    CodeRecognizer cr = new CodeRecognizer(0.8, new FakeFootprint());

    assertThat(cr.isLineOfCode("}")).isTrue();
    assertThat(cr.isLineOfCode("squid")).isFalse();
  }

  @Test
  public void extractCodeLines() {
    CodeRecognizer cr = new CodeRecognizer(0.8, new FakeFootprint());

    assertThat(cr.extractCodeLines(Arrays.asList("{", "squid"))).containsOnly("{");
  }

}
