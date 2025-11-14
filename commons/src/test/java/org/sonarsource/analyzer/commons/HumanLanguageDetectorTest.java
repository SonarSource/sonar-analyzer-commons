/*
 * SonarSource Analyzers Commons
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
package org.sonarsource.analyzer.commons;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static java.util.stream.Stream.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class HumanLanguageDetectorTest {

  static Stream<Arguments> testHumanLanguageScore() {
    return of(
      arguments("Hello02139710238712987", 1.3262863),
      arguments("This is an english text!", 4.6352161),
      arguments("Hello", 4.7598878),
      arguments("Hello hello hello hello", 4.7598878),
      arguments("Hleol", 2.0215728),
      arguments("Hleol hleol", 2.0215728),
      arguments("Hleol Hello hleol", 2.9343445),
      arguments("Hleol Incomprehensibility hleol", 3.5209606),
      arguments("Incomprehensibility ", 4.3978577),
      arguments("slrwaxquavy", 0.783135),
      arguments("SlRwAxQuAvY", 0.5729079),
      arguments("012345678", 1.0),
      arguments("images/blob/50281d86d6ed5c61975971150adf", 1.1821769),
      arguments("js/commit/8863b9d04c722b278fa93c5d66ad1e", 0.9126614),
      arguments("net/core/builder/e426a9ae7167c5807b173d5", 1.9399531),
      arguments("net/more/builder/3ad489866f41084fa4f3307", 1.9014789),
      arguments("project/commit/c5acf965067478784b54e2d24", 1.2177787),
      arguments("/var/lib/openshift/51122e382d5271c5ca000", 1.3230153),
      arguments("examples/commit/16ad89c4172c259f15bce56e", 1.6869377),
      arguments("examples/commit/8e1d746900f5411e9700fea0", 1.48724),
      arguments("examples/commit/c95b6a84b6fd1efc832a46cd", 1.503256),
      arguments("examples/commit/d6f6ef7457d99e31990fa64b", 1.4204883),
      arguments("examples/commit/ea15f07ce79366a08fee5b60", 1.8357153),
      arguments("cn/res/chinapostplan/structure/181041269", 3.494024),
      arguments("com/istio/proxy/blob/bcdc1684df0839a6125", 1.5356048),
      arguments("com/kriskowal/q/blob/b0fa72980717dc202ff", 1.3069352),
      arguments("com/ph/logstash/de2ba3f964ae7039b7b74a4a", 1.4612998),
      arguments("default/src/test/java/org/xwiki/componen", 2.6909549),
      arguments("search_my_organization-example.json", 3.6890879),
      arguments("org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH", 3.5768316),
      arguments("org.apache.catalina.startup.EXIT_ON_INIT_FAILURE", 4.2315959),
      arguments("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/", 1.2038558),
      arguments("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_", 1.2038558),
      arguments("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567", 1.2252754),
      arguments("0123456789ABCDEFGHIJKLMNOPQRSTUV", 1.2310129),
      arguments("abcdefghijklmnopqrstuvwxyz", 1.1127479),
      arguments("ABCDEFGHIJKLMNOPQRSTUVWXYZ", 1.1127479),
      arguments("org.eclipse.jetty.server.HttpChannelState.DEFAULT_TIMEOUT", 3.2985092),
      arguments("org.apache.tomcat.websocket.WS_AUTHENTICATION_PASSWORD", 4.061177),
      arguments("éclair", 2.3049092),
      arguments("clair", 2.3049092),
      arguments("éclair żółć", 1.2024546)
    );
  }

  @ParameterizedTest
  @MethodSource
  void testHumanLanguageScore(String text, Double expected) {
    assertThat(HumanLanguageDetector.humanLanguageScore(text)).isEqualTo(expected, offset(0.0000001));
  }
}
