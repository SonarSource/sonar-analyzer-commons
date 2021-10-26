/*
 * SonarSource Analyzers Regex Parsing Commons
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
package org.sonarsource.analyzer.commons.regex.java;

import java.util.EnumSet;
import java.util.Set;
import org.sonarsource.analyzer.commons.regex.CharacterParser;
import org.sonarsource.analyzer.commons.regex.RegexDialect;
import org.sonarsource.analyzer.commons.regex.RegexFeature;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class JavaRegexSource implements RegexSource {

  private static final Set<RegexFeature> FEATURES = EnumSet.of(
    RegexFeature.JAVA_SYNTAX_GROUP_NAME
  );
  private final String sourceText;

  public JavaRegexSource(String sourceText) {
    this.sourceText = sourceText;
  }

  @Override
  public String getSourceText() {
    return sourceText;
  }

  @Override
  public CharacterParser createCharacterParser() {
    return new JavaCharacterParser(this);
  }

  @Override
  public RegexDialect dialect() {
    return RegexDialect.JAVA;
  }

  @Override
  public Set<RegexFeature> features() {
    return FEATURES;
  }

  @Override
  public boolean supportFeature(RegexFeature feature) {
    return FEATURES.contains(feature);
  }
}
