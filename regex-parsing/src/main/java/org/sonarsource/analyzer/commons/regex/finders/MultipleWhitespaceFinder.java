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
package org.sonarsource.analyzer.commons.regex.finders;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.regex.Pattern;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.RegexSource;
import org.sonarsource.analyzer.commons.regex.ast.CharacterTree;
import org.sonarsource.analyzer.commons.regex.ast.IndexRange;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.SequenceTree;

public class MultipleWhitespaceFinder extends RegexBaseVisitor {

  private static final String MESSAGE = "Replace spaces with quantifier `{%s}`.";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;

  public MultipleWhitespaceFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }

  @Override
  public void visitSequence(SequenceTree sequence) {

    if (!sequence.activeFlags().contains(Pattern.COMMENTS)) {
      Deque<RegexTree> whitespaces = new ArrayDeque<>();
      for (RegexTree item : sequence.getItems()) {
        if (isWhitespace(item)) {
          whitespaces.add(item);
        } else if (whitespaces.size() > 1) {
          reportMultipleWhitespaces(sequence, whitespaces);
          whitespaces.clear();
        } else {
          whitespaces.clear();
        }
      }
      // check if sequence ends with multiple whitespaces
      if (whitespaces.size() > 1) {
        reportMultipleWhitespaces(sequence, whitespaces);
      }
    }

    super.visitSequence(sequence);
  }

  private static boolean isWhitespace(RegexTree element) {
    return element.is(RegexTree.Kind.CHARACTER) && " ".equals(((CharacterTree) element).characterAsString());
  }

  private void reportMultipleWhitespaces(SequenceTree parentSequence, Deque<RegexTree> whitespaces) {
    // remove first whitespace to only report repeated whitespace
    whitespaces.removeFirst();
    regexElementIssueReporter.report(SubSequence.fromSequence(parentSequence, whitespaces), String.format(MESSAGE, whitespaces.size() + 1), null, Collections.emptyList());
  }

  static class SubSequence implements RegexSyntaxElement {

    private final RegexSource source;
    private final IndexRange range;

    private SubSequence(RegexSource source, IndexRange range) {
      this.source = source;
      this.range = range;
    }

    protected static SubSequence fromSequence(SequenceTree parentSequence, Deque<RegexTree> items) {
      IndexRange range = new IndexRange(items.getFirst().getRange().getBeginningOffset(), items.getLast().getRange().getEndingOffset());
      return new SubSequence(parentSequence.getSource(), range);
    }

    @Override
    public String getText() {
      throw new UnsupportedOperationException();
    }

    @Override
    public IndexRange getRange() {
      return range;
    }

    @Override
    public RegexSource getSource() {
      return source;
    }
  }
}
