/*
 * SonarSource Analyzers Recognizers
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EndWithDetectorTest {

  @Test
  public void scan() {
    EndWithDetector detector = new EndWithDetector(0.3, '}');
    assertThat(detector.scan(" return true; }")).isOne();
    assertThat(detector.scan("} catch(NullPointerException e) {")).isZero();
    assertThat(detector.scan("} ")).isOne();
    assertThat(detector.scan("}*")).isOne();
    assertThat(detector.scan("}/")).isOne();
    assertThat(detector.scan("")).isZero();
  }
}
