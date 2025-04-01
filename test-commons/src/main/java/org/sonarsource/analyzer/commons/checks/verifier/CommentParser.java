/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2025 SonarSource SA
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
package org.sonarsource.analyzer.commons.checks.verifier;

import java.nio.file.Path;
import org.sonarsource.analyzer.commons.checks.verifier.internal.InternalCommentParser;

public interface CommentParser {

  static CommentParser create() {
    return new InternalCommentParser();
  }

  /**
   * <pre>
   * This comment parser is able split a line using "commentPrefix".
   * But there's some limitation, like for this case in java:
   * String name = "Paul//Smith"; // Noncompliant
   * Example:
   *   parser.addSingleLineCommentSyntax("//");
   * </pre>
   */
  CommentParser addSingleLineCommentSyntax(String commentPrefix);

  /**
   * @param path source file to parse
   * @param verifier verifier for feed
   */
  void parseInto(Path path, MultiFileVerifier verifier);

  /**
   * @param path source file to parse
   * @param verifier verifier for feed
   */
  void parseInto(Path path, SingleFileVerifier verifier);

}
