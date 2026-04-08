/*
 * SonarSource Analyzers Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
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
package org.sonarsource.analyzer.commons;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.sonar.api.batch.sensor.issue.IssueResolution;
import org.sonar.api.rule.RuleKey;

public final class SonarResolve {

  public static final String KEYWORD = "sonar-resolve";

  private final int line;
  private final IssueResolution.Status status;
  private final Set<RuleKey> ruleKeys;
  private final String justification;

  public SonarResolve(int line, IssueResolution.Status status, Set<RuleKey> ruleKeys, String justification) {
    this.line = line;
    this.status = Objects.requireNonNull(status);
    this.ruleKeys = Collections.unmodifiableSet(new LinkedHashSet<>(ruleKeys));
    this.justification = Objects.requireNonNull(justification);
  }

  public int line() {
    return line;
  }

  public IssueResolution.Status status() {
    return status;
  }

  public Set<RuleKey> ruleKeys() {
    return ruleKeys;
  }

  public String justification() {
    return justification;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof SonarResolve)) {
      return false;
    }
    SonarResolve other = (SonarResolve) object;
    return line == other.line
      && status == other.status
      && ruleKeys.equals(other.ruleKeys)
      && justification.equals(other.justification);
  }

  @Override
  public int hashCode() {
    return Objects.hash(line, status, ruleKeys, justification);
  }

  @Override
  public String toString() {
    return "SonarResolve{"
      + "line=" + line
      + ", status=" + status
      + ", ruleKeys=" + ruleKeys
      + ", justification='" + justification + '\''
      + '}';
  }

  public static final class Driver {

    public enum State {
      INCOMPLETE,
      COMPLETE,
      INVALID
    }

    private static final String PREFIX = "Invalid sonar-resolve directive: ";

    private final StringBuilder accumulatedDirective = new StringBuilder();
    private final int line;
    private State state = State.INCOMPLETE;
    private SonarResolve result;
    private String errorMessage;

    public Driver(int line) {
      this.line = line;
    }

    public State consumeLine(String normalizedLine) {
      checkState(state == State.INCOMPLETE, "Cannot consume additional lines after parser reached a terminal state.");
      appendNormalizedLine(normalizedLine);
      Parser parser = new Parser(line);
      update(parser, parser.parse(accumulatedDirective.toString()));
      return state;
    }

    public State finish() {
      checkState(accumulatedDirective.length() > 0, "Cannot finish parser before consuming any lines.");
      if (state == State.INCOMPLETE) {
        state = State.INVALID;
        result = null;
      }
      return state;
    }

    public State state() {
      return state;
    }

    public SonarResolve result() {
      checkState(state == State.COMPLETE, "Result is only available when parsing completed successfully.");
      return result;
    }

    public String errorMessage() {
      checkState(state == State.INVALID, "Error message is only available when parsing failed.");
      return PREFIX + errorMessage;
    }

    private void appendNormalizedLine(String normalizedLine) {
      if (accumulatedDirective.length() > 0) {
        accumulatedDirective.append('\n');
      }
      accumulatedDirective.append(normalizedLine);
    }

    private void update(Parser parser, State parserState) {
      state = parserState;
      result = parser.result();
      errorMessage = parser.errorMessage();
    }

    private static void checkState(boolean expression, String message) {
      if (!expression) {
        throw new IllegalStateException(message);
      }
    }

    private static final class Parser {

      private final int line;
      private State state = State.INCOMPLETE;
      private String errorMessage;
      private IssueResolution.Status status = IssueResolution.Status.DEFAULT;
      private final Set<RuleKey> ruleKeys = new LinkedHashSet<>();
      private String justification;

      private Parser(int line) {
        this.line = line;
      }

      private State parse(String accumulatedDirective) {
        Cursor cursor = new Cursor(accumulatedDirective);
        cursor.skipWhitespace();

        if (!cursor.expectLiteral(KEYWORD, "missing '" + KEYWORD + "'")) {
          return state;
        }

        if (!cursor.expectWhitespace("expected whitespace after '" + KEYWORD + "'")) {
          return state;
        }

        if (!parseStatus(cursor)) {
          return state;
        }

        if (!parseRuleKeys(cursor)) {
          return state;
        }

        if (!parseJustification(cursor)) {
          return state;
        }

        state = State.COMPLETE;
        return state;
      }

      private SonarResolve result() {
        if (state != State.COMPLETE) {
          return null;
        }
        return new SonarResolve(line, status, ruleKeys, justification);
      }

      private String errorMessage() {
        return errorMessage;
      }

      private boolean parseStatus(Cursor cursor) {
        cursor.skipWhitespace();
        if (!cursor.consume('[')) {
          status = IssueResolution.Status.DEFAULT;
          return true;
        }

        String statusText = cursor.consumeUntil(']');
        if (statusText == null) {
          return incomplete("unterminated status");
        }

        if ("accept".equals(statusText)) {
          status = IssueResolution.Status.DEFAULT;
          return true;
        }
        if ("fp".equals(statusText)) {
          status = IssueResolution.Status.FALSE_POSITIVE;
          return true;
        }
        return invalid("invalid status '[" + statusText + "]'");
      }

      private boolean parseRuleKeys(Cursor cursor) {
        ruleKeys.clear();
        while (true) {
          cursor.skipWhitespace();
          if (cursor.isAtEnd()) {
            return incomplete(ruleKeys.isEmpty() ? "missing rule key" : "invalid rule key list");
          }

          if (!parseSingleRuleKey(cursor)) {
            return false;
          }

          cursor.skipWhitespace();
          if (!cursor.consume(',')) {
            return true;
          }
        }
      }

      private boolean parseSingleRuleKey(Cursor cursor) {
        String ruleKeyText = cursor.consumeWhile(Parser::isRuleKeyChar);
        if (ruleKeyText.isEmpty()) {
          if (ruleKeys.isEmpty()) {
            return invalid("missing rule key");
          }
          return invalid("invalid rule key list");
        }

        RuleKey ruleKey;
        try {
          ruleKey = RuleKey.parse(ruleKeyText);
        } catch (IllegalArgumentException exception) {
          return invalid("invalid rule key '" + ruleKeyText + "'");
        }
        if (!ruleKeys.add(ruleKey)) {
          return invalid("duplicate rule key '" + ruleKey + "'");
        }
        return true;
      }

      private boolean parseJustification(Cursor cursor) {
        cursor.skipWhitespace();
        if (cursor.isAtEnd()) {
          return incomplete("missing justification");
        }
        if (!cursor.consume('"')) {
          return invalid("missing justification");
        }

        StringBuilder justificationBuilder = new StringBuilder();
        boolean escaped = false;
        while (!cursor.isAtEnd()) {
          char current = cursor.consume();
          if (escaped) {
            justificationBuilder.append(unescape(current));
            escaped = false;
          } else if (current == '\\') {
            escaped = true;
          } else if (current == '"') {
            justification = justificationBuilder.toString();
            return true;
          } else {
            justificationBuilder.append(current);
          }
        }
        return incomplete("unterminated justification");
      }

      private static char unescape(char character) {
        switch (character) {
          case 'n':
            return '\n';
          case 'r':
            return '\r';
          case 't':
            return '\t';
          default:
            return character;
        }
      }

      private boolean incomplete(String message) {
        state = State.INCOMPLETE;
        errorMessage = message;
        justification = null;
        return false;
      }

      private boolean invalid(String message) {
        state = State.INVALID;
        errorMessage = message;
        justification = null;
        return false;
      }

      private static boolean isRuleKeyChar(char character) {
        return Character.isLetterOrDigit(character) || character == ':' || character == '_';
      }

      private final class Cursor {

        private final String text;
        private int index;

        private Cursor(String text) {
          this.text = text;
        }

        private boolean isAtEnd() {
          return index == text.length();
        }

        private void skipWhitespace() {
          consumeWhile(Character::isWhitespace);
        }

        private boolean expectLiteral(String literal, String missingMessage) {
          return text.startsWith(literal, index) ? advance(literal.length()) : invalid(missingMessage);
        }

        private boolean expectWhitespace(String message) {
          if (isAtEnd()) {
            return true;
          }
          if (!Character.isWhitespace(text.charAt(index))) {
            return invalid(message);
          }
          skipWhitespace();
          return true;
        }

        private boolean advance(int length) {
          index += length;
          return true;
        }

        private boolean consume(char expected) {
          if (!isAtEnd() && text.charAt(index) == expected) {
            index++;
            return true;
          }
          return false;
        }

        private char consume() {
          char character = text.charAt(index);
          index++;
          return character;
        }

        private String consumeWhile(CharacterPredicate predicate) {
          int start = index;
          while (!isAtEnd() && predicate.test(text.charAt(index))) {
            index++;
          }
          return text.substring(start, index);
        }

        private String consumeUntil(char delimiter) {
          String consumed = consumeWhile(character -> character != delimiter);
          return consume(delimiter) ? consumed : null;
        }
      }

      private interface CharacterPredicate {
        boolean test(char character);
      }
    }
  }
}
