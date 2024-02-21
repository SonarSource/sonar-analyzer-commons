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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.sonarsource.analyzer.commons.checks.verifier.CommentParser;
import org.sonarsource.analyzer.commons.checks.verifier.FileContent;
import org.sonarsource.analyzer.commons.checks.verifier.MultiFileVerifier;
import org.sonarsource.analyzer.commons.checks.verifier.SingleFileVerifier;

public class InternalCommentParser implements CommentParser {

  private List<Comment.Parser> commentParsers = new ArrayList<>();

  @Override
  public InternalCommentParser addSingleLineCommentSyntax(String commentPrefix) {
    commentParsers.add(new SingleLineCommentParser(commentPrefix));
    return this;
  }

  @Override
  public void parseInto(Path path, MultiFileVerifier verifier) {
    ((InternalIssueVerifier) verifier).addComments(parse(path));
  }

  @Override
  public void parseInto(Path path, SingleFileVerifier verifier) {
    ((InternalIssueVerifier) verifier).addComments(parse(path));
  }

  private List<Comment> parse(Path path) {
    FileContent file = new FileContent(path.toAbsolutePath());
    List<Comment> comments = new ArrayList<>();
    for (Comment.Parser parser : commentParsers) {
      comments.addAll(parser.parse(file));
    }
    comments.sort((a,b) -> Integer.compare(a.line, b.line) * 2 + Integer.compare(a.column, b.column));
    return comments;
  }

}
