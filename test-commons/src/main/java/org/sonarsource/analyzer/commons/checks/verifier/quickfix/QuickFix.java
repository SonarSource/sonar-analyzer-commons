/*
 * SonarSource Analyzers Quickfixes
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
package org.sonarsource.analyzer.commons.checks.verifier.quickfix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class QuickFix {

  private final String description;
  private final List<TextEdit> textEdits;

  private QuickFix(String description, List<TextEdit> textEdits) {
    this.description = description;
    this.textEdits = textEdits;
  }

  public String getDescription() {
    return description;
  }

  public List<TextEdit> getTextEdits() {
    return textEdits;
  }

  /**
   * See org.sonar.api.batch.sensor.issue.fix.NewQuickFix#message(String) for guidelines on format of the description.
   *
   * @param description a description for this quick fix
   * @return the builder for this quick fix
   */
  public static Builder newQuickFix(String description) {
    return new Builder(description);
  }

  /**
   * See org.sonar.api.batch.sensor.issue.fix.NewQuickFix#message(String) for guidelines on format of the description.
   *
   * @param description a description for this quick fix, following the {@link String#format(String, Object...)} formatting
   * @param args        the arguments for the description
   * @return the builder for this quick fix
   */
  public static Builder newQuickFix(String description, Object... args) {
    return new Builder(String.format(description, args));
  }

  public static class Builder {
    private final String description;
    private final List<TextEdit> textEdits = new ArrayList<>();

    private Builder(String description) {
      this.description = description;
    }

    public Builder addTextEdit(TextEdit... textEdit) {
      textEdits.addAll(Arrays.asList(textEdit));
      return this;
    }

    public Builder addTextEdits(List<TextEdit> textEdits) {
      this.textEdits.addAll(textEdits);
      return this;
    }

    public Builder reverseSortEdits() {
      textEdits.sort(new Sorter().reversed());
      return this;
    }

    public QuickFix build() {
      return new QuickFix(description, textEdits);
    }

    private static class Sorter implements Comparator<TextEdit> {

      @Override
      public int compare(TextEdit a, TextEdit b) {
        TextSpan first = a.getTextSpan();
        TextSpan second = b.getTextSpan();

        int result = first.startLine - second.startLine;
        if (result != 0) {
          return result;
        }
        result = first.startCharacter - second.startCharacter;
        if (result != 0) {
          return result;
        }
        result = first.endLine - second.endLine;
        if (result != 0) {
          return result;
        }
        return first.endCharacter - second.endCharacter;
      }
    }
  }

}
