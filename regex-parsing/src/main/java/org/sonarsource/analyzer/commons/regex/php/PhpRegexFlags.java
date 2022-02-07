/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2022 SonarSource SA
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
package org.sonarsource.analyzer.commons.regex.php;

import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;

public class PhpRegexFlags {

  public static final int PCRE_CASELESS = Pattern.CASE_INSENSITIVE;
  public static final int PCRE_MULTILINE = Pattern.MULTILINE;
  public static final int PCRE_DOTALL = Pattern.DOTALL;
  public static final int PCRE_EXTENDED = Pattern.COMMENTS;
  public static final int PCRE_UTF8 = Pattern.UNICODE_CHARACTER_CLASS;

  private PhpRegexFlags() {

  }

  public static FlagSet parseFlags(String flags) {
    FlagSet flagSet = new FlagSet();
    for (char modifier: flags.toCharArray()) {
      Optional.ofNullable(parseFlag(modifier)).ifPresent(flagSet::add);
    }
    return flagSet;
  }

  @CheckForNull
  public static Integer parseFlag(char ch) {
    switch (ch) {
      case 'i':
        return PCRE_CASELESS;
      case 'm':
        return PCRE_MULTILINE;
      case 's':
        return PCRE_DOTALL;
      case 'u':
        return PCRE_UTF8;
      case 'x':
        return PCRE_EXTENDED;
      default:
        return null;
    }
  }
}
