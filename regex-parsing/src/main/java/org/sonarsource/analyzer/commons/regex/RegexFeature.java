/*
 * SonarSource Analyzers Regex Parsing Commons
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
package org.sonarsource.analyzer.commons.regex;

public enum RegexFeature {
  RECURSION,                                          // (?R)
  CONDITIONAL_SUBPATTERN,                             // (?(\1)a|b)
  POSIX_CHARACTER_CLASS,                              // [[:alpha:]]
  PYTHON_SYNTAX_GROUP_NAME,                           // (P<name>)
  PYTHON_OCTAL_ESCAPE,                                // \\377
  DOTNET_SYNTAX_GROUP_NAME,                           // (?<name>) and (?'name')
  PERL_SYNTAX_GROUP_NAME,                             // \k{name} and \g{name}
  JAVA_SYNTAX_GROUP_NAME,                             // (?<name>)
  BACKSLASH_ESCAPING,                                      // //// vs //
  ATOMIC_GROUP,                                       // (?>a)
  POSSESSIVE_QUANTIFIER,                              // x++k
  ESCAPED_CHARACTER_CLASS,                            // \p{Lower}
  UNESCAPED_CURLY_BRACKET,                            // x{1a
  ONLY_UPPER_BOUND_QUANTIFIER,                        // x{,3}
  NESTED_CHARTER_CLASS,                               // [a[bc]]
  PHP_BINARY_ZERO                                     // \0
}
