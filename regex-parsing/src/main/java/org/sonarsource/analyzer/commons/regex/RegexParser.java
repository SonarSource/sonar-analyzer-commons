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
package org.sonarsource.analyzer.commons.regex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.analyzer.commons.regex.ast.AtomicGroupTree;
import org.sonarsource.analyzer.commons.regex.ast.BackReferenceTree;
import org.sonarsource.analyzer.commons.regex.ast.BoundaryTree;
import org.sonarsource.analyzer.commons.regex.ast.CapturingGroupTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassElementTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassIntersectionTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassUnionTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterRangeTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterTree;
import org.sonarsource.analyzer.commons.regex.ast.ConditionalSubpatternTree;
import org.sonarsource.analyzer.commons.regex.ast.CurlyBraceQuantifier;
import org.sonarsource.analyzer.commons.regex.ast.DisjunctionTree;
import org.sonarsource.analyzer.commons.regex.ast.DotTree;
import org.sonarsource.analyzer.commons.regex.ast.EscapedCharacterClassTree;
import org.sonarsource.analyzer.commons.regex.ast.FinalState;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.GroupTree;
import org.sonarsource.analyzer.commons.regex.ast.IndexRange;
import org.sonarsource.analyzer.commons.regex.ast.LookAroundTree;
import org.sonarsource.analyzer.commons.regex.ast.MiscEscapeSequenceTree;
import org.sonarsource.analyzer.commons.regex.ast.NonCapturingGroupTree;
import org.sonarsource.analyzer.commons.regex.ast.PosixCharacterClassElementTree;
import org.sonarsource.analyzer.commons.regex.ast.Quantifier;
import org.sonarsource.analyzer.commons.regex.ast.ReferenceConditionTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;
import org.sonarsource.analyzer.commons.regex.ast.RegexToken;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.RepetitionTree;
import org.sonarsource.analyzer.commons.regex.ast.SequenceTree;
import org.sonarsource.analyzer.commons.regex.ast.SimpleQuantifier;
import org.sonarsource.analyzer.commons.regex.ast.SourceCharacter;
import org.sonarsource.analyzer.commons.regex.ast.StartState;

import static org.sonarsource.analyzer.commons.regex.RegexLexer.EOF;

public class RegexParser {

  private static final Logger LOG = Loggers.get(RegexParser.class);

  private static final String HEX_DIGIT = "hexadecimal digit";

  private static final String POSIX_CHARACTER_CLASS_PATTERN = "[:%s%s:]";

  private static final Set<String> POSIX_CHARACTER_CLASSES = new HashSet<>(Arrays.asList(
    "alnum", "alpha", "ascii", "blank", "cntrl", "digit", "graph", "lower", "print", "punct", "space", "upper", "word", "xdigit", "<", ">"
  ));

  private static final Map<String, String> POSIX_CHARACTER_CLASS_LOOKUP = posixCharacterClassMap(false);

  private static final Map<String, String> POSIX_CHARACTER_CLASS_NEGATION_LOOKUP = posixCharacterClassMap(true);

  private static Map<String, String> posixCharacterClassMap(boolean negative) {
    return POSIX_CHARACTER_CLASSES.stream()
      .collect(Collectors.toMap(posix -> String.format(POSIX_CHARACTER_CLASS_PATTERN, negative ? "^" : "", posix), posix -> posix));
  }

  protected final RegexSource source;

  protected final RegexLexer characters;

  protected FlagSet activeFlags;

  protected final List<BackReferenceTree> backReferences = new ArrayList<>();

  protected final Map<String, CapturingGroupTree> capturingGroups = new HashMap<>();

  protected final List<SyntaxError> errors = new ArrayList<>();

  protected int groupNumber = 1;

  public RegexParser(RegexSource source, FlagSet initialFlags) {
    this.source = source;
    this.characters = source.createLexer();
    this.characters.setFreeSpacingMode(initialFlags.contains(Pattern.COMMENTS));
    this.activeFlags = initialFlags;
  }

  public RegexParseResult parse() {
    FlagSet initialFlags = activeFlags;
    List<RegexTree> results = new ArrayList<>();
    do {
      RegexTree result = parseDisjunction();
      results.add(result);
      if (characters.isNotAtEnd()) {
        error("Unexpected '" + characters.getCurrent().getCharacter() + "'");
        characters.moveNext();
      }
    } while (characters.isNotAtEnd());
    if (characters.isInQuotingMode()) {
      expected("'\\E'");
    }
    RegexTree result = combineTrees(results, (range, elements) -> new SequenceTree(source, range, elements, initialFlags));
    StartState startState = new StartState(result, initialFlags);
    FinalState finalState = new FinalState(activeFlags);
    result.setContinuation(finalState);
    backReferences.forEach(reference -> reference.setGroup(capturingGroups.get(reference.groupName())));
    return new RegexParseResult(result, startState, finalState, errors, characters.hasComments());
  }

  protected RegexTree parseDisjunction() {
    FlagSet disjunctionFlags = activeFlags;
    List<RegexTree> alternatives = new ArrayList<>();
    List<SourceCharacter> orOperators = new ArrayList<>();
    RegexTree first = parseSequence();
    alternatives.add(first);
    while (characters.currentIs('|')) {
      orOperators.add(characters.getCurrent());
      characters.moveNext();
      RegexTree next = parseSequence();
      alternatives.add(next);
    }
    return combineTrees(alternatives, (range, elements) -> new DisjunctionTree(source, range, elements, orOperators, disjunctionFlags));
  }

  protected RegexTree parseSequence() {
    FlagSet sequenceFlags = activeFlags;
    List<RegexTree> elements = new ArrayList<>();
    RegexTree element = parseRepetition();
    while (element != null) {
      elements.add(element);
      element = parseRepetition();
    }
    if (elements.isEmpty()) {
      int index = characters.getCurrentStartIndex();
      return new SequenceTree(source, new IndexRange(index, index), elements, sequenceFlags);
    } else {
      return combineTrees(elements, (range, items) -> new SequenceTree(source, range, items, sequenceFlags));
    }
  }

  @CheckForNull
  protected RegexTree parseRepetition() {
    FlagSet repetitionFlags = activeFlags;
    RegexTree element = parsePrimaryExpression();
    if (characters.isInQuotingMode()) {
      return element;
    }
    Quantifier quantifier = parseQuantifier();
    if (element == null) {
      if (quantifier != null) {
        errors.add(new SyntaxError(quantifier, "Unexpected quantifier '" + quantifier.getText() + "'"));
      }
      return null;
    }
    if (quantifier == null) {
      return element;
    } else {
      return new RepetitionTree(source, element.getRange().merge(quantifier.getRange()), element, quantifier, repetitionFlags);
    }
  }

  @CheckForNull
  protected Quantifier parseQuantifier() {
    SimpleQuantifier.Kind kind;
    switch (characters.getCurrentChar()) {
      case '*':
        kind = SimpleQuantifier.Kind.STAR;
        break;
      case '+':
        kind = SimpleQuantifier.Kind.PLUS;
        break;
      case '?':
        kind = SimpleQuantifier.Kind.QUESTION_MARK;
        break;
      case '{':
        return parseCurlyBraceQuantifier();
      default:
        return null;
    }
    SourceCharacter current = characters.getCurrent();
    characters.moveNext();
    Quantifier.Modifier modifier = parseQuantifierModifier();
    IndexRange range = current.getRange().extendTo(characters.getCurrentStartIndex());
    return new SimpleQuantifier(source, range, modifier, kind);
  }

  CurlyBraceQuantifier parseCurlyBraceQuantifier() {
    if (supportsAnyOfFeatures(RegexFeature.UNESCAPED_CURLY_BRACKET) && !isCurlyBraceQuantifier()) {
      return null;
    }
    SourceCharacter openingBrace = characters.getCurrent();
    characters.moveNext();
    RegexToken lowerBound = parseInteger();
    if (lowerBound == null) {
      expected("integer");
      return null;
    }
    RegexToken comma = null;
    RegexToken upperBound = null;
    if (characters.currentIs(',')) {
      comma = new RegexToken(source, characters.getCurrent().getRange());
      characters.moveNext();
      upperBound = parseInteger();
    }
    Quantifier.Modifier modifier;
    if (characters.currentIs('}')) {
      characters.moveNext();
    } else {
      if (comma == null) {
        expected("',' or '}'");
      } else if (upperBound == null) {
        expected("integer or '}'");
      } else {
        expected("'}'");
      }
    }
    modifier = parseQuantifierModifier();
    IndexRange range = openingBrace.getRange().extendTo(characters.getCurrentStartIndex());
    return new CurlyBraceQuantifier(source, range, modifier, lowerBound, comma, upperBound);
  }

  private boolean isCurlyBraceQuantifier() {
    int index = 1;
    if (!isAsciiDigit(characters.lookAhead(index))) {
      return false;
    }
    do {
      index++;
    } while (isAsciiDigit(characters.lookAhead(index)));
    if (characters.lookAhead(index) == '}') {
      return true;
    }
    if (characters.lookAhead(index) != ',') {
      return false;
    }
    do {
      index++;
    } while (isAsciiDigit(characters.lookAhead(index)));
    return characters.lookAhead(index) == '}';
  }

  Quantifier.Modifier parseQuantifierModifier() {
    if (characters.currentIs('?')) {
      characters.moveNext();
      return Quantifier.Modifier.RELUCTANT;
    } else if (characters.currentIs('+') && supportsAnyOfFeatures(RegexFeature.POSSESSIVE_QUANTIFIER)) {
      characters.moveNext();
      return Quantifier.Modifier.POSSESSIVE;
    } else {
      return Quantifier.Modifier.GREEDY;
    }
  }

  @CheckForNull
  protected RegexToken parseInteger() {
    int startIndex = characters.getCurrentStartIndex();
    if (!isAsciiDigit(characters.getCurrentChar())) {
      return null;
    }
    while(isAsciiDigit(characters.getCurrentChar())) {
      characters.moveNext();
    }
    IndexRange range = new IndexRange(startIndex, characters.getCurrentStartIndex());
    return new RegexToken(source, range);
  }

  @CheckForNull
  protected RegexTree parsePrimaryExpression() {
    if (characters.isInQuotingMode() && characters.isNotAtEnd()) {
      return readCharacter();
    }
    switch (characters.getCurrentChar()) {
      case '(':
        if (characters.currentIs("(?P=") && supportsAnyOfFeatures(RegexFeature.PYTHON_SYNTAX_GROUP_NAME)) {
          return parsePythonBackReference();
        }
        return parseGroup();
      case '\\':
        return parseEscapeSequence();
      case '[':
        return parseCharacterClass();
      case '.':
        DotTree tree = new DotTree(source, characters.getCurrentIndexRange(), activeFlags);
        characters.moveNext();
        return tree;
      case '^':
        BoundaryTree lineStart = new BoundaryTree(source, BoundaryTree.Type.LINE_START, characters.getCurrentIndexRange(), activeFlags);
        characters.moveNext();
        return lineStart;
      case '$':
        BoundaryTree lineEnd = new BoundaryTree(source, BoundaryTree.Type.LINE_END, characters.getCurrentIndexRange(), activeFlags);
        characters.moveNext();
        return lineEnd;
      default:
        if (isPlainTextCharacter(characters.getCurrentChar())) {
          return readCharacter();
        } else {
          return null;
        }
    }
  }

  private RegexTree parsePythonBackReference() {
    SourceCharacter openingParen = characters.getCurrent();
    // Discard '(?'
    characters.moveNext(2);
    return parseEscapedSequence('=', ')', "a group name",
      dh -> collect(new BackReferenceTree(source, openingParen, null, dh.opener, dh.closer, activeFlags)));
  }

  protected CharacterTree readCharacter() {
    SourceCharacter character = characters.getCurrent();
    characters.moveNext();
    return characterTree(character);
  }

  protected GroupTree parseGroup() {
    SourceCharacter openingParen = characters.getCurrent();
    characters.moveNext();
    if (characters.currentIs("?=")) {
      characters.moveNext(2);
      return finishGroup(openingParen, (range, inner) -> LookAroundTree.positiveLookAhead(source, range, inner, activeFlags));
    } else if (characters.currentIs("?<=")) {
      characters.moveNext(3);
      return finishGroup(openingParen, (range, inner) -> LookAroundTree.positiveLookBehind(source, range, inner, activeFlags));
    } else if (characters.currentIs("?!")) {
      characters.moveNext(2);
      return finishGroup(openingParen, (range, inner) -> LookAroundTree.negativeLookAhead(source, range, inner, activeFlags));
    } else if (characters.currentIs("?<!")) {
      characters.moveNext(3);
      return finishGroup(openingParen, (range, inner) -> LookAroundTree.negativeLookBehind(source, range, inner, activeFlags));
    } else if (characters.currentIs("?>") && supportsAnyOfFeatures(RegexFeature.ATOMIC_GROUP)) {
      characters.moveNext(2);
      return finishGroup(openingParen, (range, inner) -> new AtomicGroupTree(source, range, inner, activeFlags));
    } else if (characters.currentIs("?<") && supportsAnyOfFeatures(RegexFeature.JAVA_SYNTAX_GROUP_NAME, RegexFeature.DOTNET_SYNTAX_GROUP_NAME)) {
      return finishGroup(openingParen, newNamedCapturingGroup(2, '>'));
    } else if (characters.currentIs("?'") && supportsAnyOfFeatures(RegexFeature.DOTNET_SYNTAX_GROUP_NAME)) {
      return finishGroup(openingParen, newNamedCapturingGroup(2, '\''));
    } else if (characters.currentIs("?P<") && supportsAnyOfFeatures(RegexFeature.PYTHON_SYNTAX_GROUP_NAME)) {
      return finishGroup(openingParen, newNamedCapturingGroup(3, '>'));
    } else if (characters.currentIs("?")) {
      return parseNonCapturingGroup(openingParen);
    } else {
      return finishGroup(openingParen, newCapturingGroup(null));
    }
  }

  protected GroupConstructor newNamedCapturingGroup(int namePrefixLength, char nameDelimiter) {
    characters.moveNext(namePrefixLength);
    String name = parseGroupName(nameDelimiter);
    if (characters.currentIs(nameDelimiter)) {
      characters.moveNext();
    } else {
      expected("'" + nameDelimiter + "'");
    }
    return newCapturingGroup(name);
  }

  protected GroupConstructor newCapturingGroup(@Nullable String name) {
    int index = groupNumber;
    groupNumber++;
    return (range, inner) -> index(new CapturingGroupTree(source, range, name, index, inner, activeFlags));
  }

  protected String parseGroupName(char nameDelimiter) {
    StringBuilder sb = new StringBuilder();
    while (characters.isNotAtEnd() && !characters.currentIs(nameDelimiter)) {
      sb.append(characters.getCurrent().getCharacter());
      characters.moveNext();
    }
    String name = sb.toString();
    if (name.isEmpty()) {
      expected("a name for the group");
    }
    return name;
  }

  protected GroupTree parseNonCapturingGroup(SourceCharacter openingParen) {
    // Discard '?'
    characters.moveNext();
    if (characters.currentIs("R)") && source.supportsFeature(RegexFeature.RECURSION)) {
      return parseRecursion(openingParen);
    }
    if (characters.currentIs("(") && source.supportsFeature(RegexFeature.CONDITIONAL_SUBPATTERN)) {
      return parseConditionalSubpattern(openingParen);
    }

    FlagSet enabledFlags = parseFlags();
    FlagSet disabledFlags;
    if (characters.currentIs('-')) {
      characters.moveNext();
      disabledFlags = parseFlags();
    } else {
      disabledFlags = new FlagSet();
    }

    boolean previousFreeSpacingMode = characters.getFreeSpacingMode();
    if (disabledFlags.contains(Pattern.COMMENTS)) {
      characters.setFreeSpacingMode(false);
    } else if (enabledFlags.contains(Pattern.COMMENTS)) {
      characters.setFreeSpacingMode(true);
    }

    FlagSet previousFlags = activeFlags;
    if (!enabledFlags.isEmpty() || !disabledFlags.isEmpty()) {
      activeFlags = new FlagSet(activeFlags);
      activeFlags.addAll(enabledFlags);
      activeFlags.removeAll(disabledFlags);
    }
    if (characters.currentIs(')')) {
      SourceCharacter closingParen = characters.getCurrent();
      characters.moveNext();
      IndexRange range = openingParen.getRange().merge(closingParen.getRange());
      return new NonCapturingGroupTree(source, range, enabledFlags, disabledFlags, null, activeFlags);
    }
    if (characters.currentIs(':')) {
      characters.moveNext();
    } else {
      expected("flag or ':' or ')'");
    }
    GroupTree group = finishGroup(previousFreeSpacingMode, openingParen, (range, inner) ->
      new NonCapturingGroupTree(source, range, enabledFlags, disabledFlags, inner, activeFlags)
    );
    activeFlags = previousFlags;
    return group;
  }

  private GroupTree parseConditionalSubpattern(SourceCharacter openingParen) {
    GroupTree condition = parseCondition();
    RegexTree subpattern = parseDisjunction();
    SourceCharacter closingParen = characters.getCurrent();
    characters.moveNext();
    if (subpattern.is(RegexTree.Kind.DISJUNCTION)) {
      if (((DisjunctionTree) subpattern).getAlternatives().size() > 2) {
        error("More than two alternatives in the subpattern");
      }
      DisjunctionTree disjunction = (DisjunctionTree) subpattern;
      return new ConditionalSubpatternTree(source, openingParen, closingParen, condition, disjunction.getAlternatives().get(0),
        disjunction.getOrOperators().get(0), disjunction.getAlternatives().get(1), activeFlags);
    } else {
      return new ConditionalSubpatternTree(source, openingParen, closingParen, condition, subpattern, activeFlags);
    }
  }

  private GroupTree parseCondition() {
    SourceCharacter openingParen = characters.getCurrent();
    characters.moveNext();
    if (characters.currentIs("?=")) {
      characters.moveNext(2);
      return finishGroup(openingParen, (range, inner) -> LookAroundTree.positiveLookAhead(source, range, inner, activeFlags));
    } else if (characters.currentIs("?<=")) {
      characters.moveNext(3);
      return finishGroup(openingParen, (range, inner) -> LookAroundTree.positiveLookBehind(source, range, inner, activeFlags));
    } else if (characters.currentIs("?!")) {
      characters.moveNext(2);
      return finishGroup(openingParen, (range, inner) -> LookAroundTree.negativeLookAhead(source, range, inner, activeFlags));
    } else if (characters.currentIs("?<!")) {
      characters.moveNext(3);
      return finishGroup(openingParen, (range, inner) -> LookAroundTree.negativeLookBehind(source, range, inner, activeFlags));
    } else if (characters.currentIs("+")) {
      // Skip '+' as first character since it would be identified as quantifier at the beginning of a sequence
      CharacterTree plus = readCharacter();
      return finishGroup(openingParen, (range, inner) -> conditionGroupReference(source, range, plus, inner, activeFlags));
    } else {
      // TODO Allow only valid conditions: signed sequence of digits or 'R'
      return finishGroup(openingParen, (range, inner) -> conditionGroupReference(source, range, null, inner, activeFlags));
    }
  }

  public ReferenceConditionTree conditionGroupReference(RegexSource source, IndexRange range, @Nullable CharacterTree plus, RegexTree inner, FlagSet activeFlags) {
    StringBuilder reference = new StringBuilder();
    if (plus != null) {
      reference.append('+');
    }
    if (inner.is(RegexTree.Kind.CHARACTER)) {
      reference.append(((CharacterTree) inner).characterAsString());
    } else if (inner.is(RegexTree.Kind.SEQUENCE)){
      ((SequenceTree) inner).getItems().stream()
        .filter(CharacterTree.class::isInstance)
        .map(i -> ((CharacterTree) i).characterAsString())
        .forEach(reference::append);
    } else {
      error("Conditional subpattern has invalid condition.");
    }
    return new ReferenceConditionTree(source, range, reference.toString(), activeFlags);
  }

  private GroupTree parseRecursion(SourceCharacter openingParen) {
    // Discard 'R'
    characters.moveNext();
    SourceCharacter closingParen = characters.getCurrent();
    characters.moveNext();
    IndexRange range = openingParen.getRange().merge(closingParen.getRange());
    return new NonCapturingGroupTree(source, range, new FlagSet(), new FlagSet(), null, activeFlags);
  }

  protected FlagSet parseFlags() {
    FlagSet flags = new FlagSet();
    while (characters.isNotAtEnd()) {
      Integer flag = parseFlag(characters.getCurrent().getCharacter());
      if (flag == null) {
        break;
      }
      flags.add(flag, characters.getCurrent());
      characters.moveNext();
    }
    return flags;
  }

  @CheckForNull
  protected static Integer parseFlag(char ch) {
    switch (ch) {
      case 'i':
        return Pattern.CASE_INSENSITIVE;
      case 'd':
        return Pattern.UNIX_LINES;
      case 'm':
        return Pattern.MULTILINE;
      case 's':
        return Pattern.DOTALL;
      case 'u':
        return Pattern.UNICODE_CASE;
      case 'x':
        return Pattern.COMMENTS;
      case 'U':
        return Pattern.UNICODE_CHARACTER_CLASS;
      default:
        return null;
    }
  }

  protected GroupTree finishGroup(SourceCharacter openingParen, GroupConstructor groupConstructor) {
    return finishGroup(characters.getFreeSpacingMode(), openingParen, groupConstructor);
  }

  protected GroupTree finishGroup(boolean previousFreeSpacingMode, SourceCharacter openingParen, GroupConstructor groupConstructor) {
    FlagSet previousFlagSet = activeFlags;
    RegexTree inner = parseDisjunction();
    activeFlags = previousFlagSet;
    characters.setFreeSpacingMode(previousFreeSpacingMode);
    if (characters.currentIs(')')) {
      characters.moveNext();
    } else {
      expected("')'");
    }
    IndexRange range = openingParen.getRange().extendTo(characters.getCurrentStartIndex());
    return groupConstructor.construct(range, inner);
  }

  protected RegexTree parseEscapeSequence() {
    SourceCharacter backslash = characters.getCurrent();
    characters.moveNext();
    if (characters.isAtEnd()) {
      expected("any character");
      return characterTree(backslash);
    } else if (isEscapedCharacterClass()) {
      return parseEscapedProperty(backslash);
    } else if (isEscapedBackReference()) {
      return parseNamedBackReference(backslash);
    } else {
      SourceCharacter character = characters.getCurrent();
      switch (character.getCharacter()) {
        case '0':
          return parseOctalEscape(backslash);
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
          return parseNumericalBackReference(backslash);
        case 'b':
        case 'B':
        case 'A':
        case 'G':
        case 'Z':
        case 'z':
          return parseBoundary(backslash);
        case 'w':
        case 'W':
        case 'd':
        case 'D':
        case 'S':
        case 's':
        case 'h':
        case 'H':
        case 'v':
        case 'V':
          return parseEscapedCharacterClass(backslash);
        case 'u':
          return parseUnicodeEscape(backslash);
        case 'x':
          return parseHexEscape(backslash);
        case 't':
        case 'n':
        case 'r':
        case 'f':
        case 'a':
        case 'e':
          characters.moveNext();
          char c = simpleEscapeToCharacter(character.getCharacter());
          IndexRange range = backslash.getRange().extendTo(characters.getCurrentStartIndex());
          return characterTree(new SourceCharacter(source, range, c, true));
        case 'c':
          return parseControlSequence(backslash);
        case 'N':
          return parseNamedUnicodeCharacter(backslash);
        case 'R':
        case 'X':
          characters.moveNext();
          return new MiscEscapeSequenceTree(source, backslash.getRange().extendTo(characters.getCurrentStartIndex()), activeFlags);
        case 'E':
          error("\\E used without \\Q");
          // Fallthrough
        default:
          characters.moveNext();
          return new CharacterTree(source, backslash.getRange().merge(character.getRange()), character.getCharacter(),
            character.isEscapeSequence(), activeFlags);
      }
    }
  }

  private boolean isEscapedCharacterClass() {
    return (characters.currentIs('p') || characters.currentIs('P')) && supportsAnyOfFeatures(RegexFeature.ESCAPED_CHARACTER_CLASS);
  }

  private boolean isEscapedBackReference() {
    return (characters.currentIs('k') && supportsAnyOfFeatures(RegexFeature.DOTNET_SYNTAX_GROUP_NAME, RegexFeature.JAVA_SYNTAX_GROUP_NAME, RegexFeature.PERL_SYNTAX_GROUP_NAME))
      || (characters.currentIs('g') && supportsAnyOfFeatures(RegexFeature.PERL_SYNTAX_GROUP_NAME));
  }

  protected RegexTree parseNamedUnicodeCharacter(SourceCharacter backslash) {
    return parseEscapedSequence('{', '}', "a Unicode character name", content ->
      // TODO: Once we move to Java 9+, use Character.codePointOf to produce a CharacterTree with the named Unicode
      //       character instead of a MiscEscapeSequenceTree and produce a syntax error for illegal character names
      new MiscEscapeSequenceTree(source, backslash.getRange().merge(content.closer.getRange()), activeFlags)
    );
  }

  protected RegexTree parseControlSequence(SourceCharacter backslash) {
    SourceCharacter c = characters.getCurrent();
    characters.moveNext();
    if (characters.isAtEnd()) {
      expected("any character");
      return characterTree(c);
    }
    char controlCharacter = (char) (0x40 ^ characters.getCurrentChar());
    characters.moveNext();
    IndexRange range = backslash.getRange().extendTo(characters.getCurrentStartIndex());
    return characterTree(new SourceCharacter(source, range, controlCharacter, true));
  }

  protected static char simpleEscapeToCharacter(char escapeCharacter) {
    switch (escapeCharacter) {
      case 't':
        return '\t';
      case 'n':
        return '\n';
      case 'r':
        return '\r';
      case 'f':
        return '\f';
      case 'a':
        return '\u0007';
      case 'e':
        return '\u001B';
      default:
        throw new IllegalArgumentException("Unsupported argument for simpleEscapeToCharacter: " + escapeCharacter);
    }
  }

  protected RegexTree parseUnicodeEscape(SourceCharacter backslash) {
    // Discard 'u'
    characters.moveNext();
    char codeUnit = (char) parseFixedAmountOfHexDigits(4);
    return characterTree(new SourceCharacter(source, backslash.getRange().extendTo(characters.getCurrentStartIndex()), codeUnit, true));
  }

  protected RegexTree parseHexEscape(SourceCharacter backslash) {
    // Discard 'x'
    characters.moveNext();
    int codePoint = 0;
    if (characters.currentIs('{')) {
      // Discard '{'
      characters.moveNext();
      if (!isHexDigit(characters.getCurrentChar())) {
        expected(HEX_DIGIT);
      }
      while (isHexDigit(characters.getCurrentChar())) {
        codePoint *= 16;
        codePoint += parseHexDigit();
      }
      if (characters.currentIs('}')) {
        characters.moveNext();
      } else {
        expected(HEX_DIGIT + " or '}'");
      }
    } else {
      codePoint = parseFixedAmountOfHexDigits(2);
    }
    IndexRange range = backslash.getRange().extendTo(characters.getCurrentStartIndex());
    CharacterTree tree = new CharacterTree(source, range, codePoint, true, activeFlags);
    if (!Character.isValidCodePoint(codePoint)) {
      errors.add(new SyntaxError(tree, "Invalid Unicode code point"));
    }
    return tree;
  }

  protected int parseFixedAmountOfHexDigits(int amount) {
    int i = 0;
    char result = 0;
    while (i < amount && isHexDigit(characters.getCurrentChar())) {
      result *= 16;
      result += parseHexDigit();
      i++;
    }
    if (i < amount) {
      expected(HEX_DIGIT);
    }
    return result;
  }

  protected int parseHexDigit() {
    int value = Integer.parseInt("" + characters.getCurrent().getCharacter(), 16);
    characters.moveNext();
    return value;
  }

  protected RegexTree parseEscapedCharacterClass(SourceCharacter backslash) {
    RegexTree result = new EscapedCharacterClassTree(source, backslash, characters.getCurrent(), activeFlags);
    characters.moveNext();
    return result;
  }

  protected RegexTree parseEscapedProperty(SourceCharacter backslash) {
    return parseEscapedSequence('{', '}', "a property name",
      dh -> new EscapedCharacterClassTree(source, backslash, dh.marker, dh.opener, dh.closer, activeFlags));
  }

  protected RegexTree parseNamedBackReference(SourceCharacter backslash) {
    if(characters.currentIs("k<") && supportsAnyOfFeatures(RegexFeature.DOTNET_SYNTAX_GROUP_NAME, RegexFeature.JAVA_SYNTAX_GROUP_NAME)) {
      return parseNamedBackReference(backslash, '<', '>');
    } else if(characters.currentIs("k'") && supportsAnyOfFeatures(RegexFeature.DOTNET_SYNTAX_GROUP_NAME)) {
      return parseNamedBackReference(backslash, '\'', '\'');
    } else if((characters.currentIs("k{") || characters.currentIs("g{")) && supportsAnyOfFeatures(RegexFeature.PERL_SYNTAX_GROUP_NAME)) {
      return parseNamedBackReference(backslash, '{', '}');
    }
    characters.moveNext();
    expectedNamedBackReferenceOpener();
    return characterTree(backslash);
  }

  protected RegexTree parseNamedBackReference(SourceCharacter backslash, char opener, char closer) {
    return parseEscapedSequence(opener, closer, "a group name",
      dh -> collect(new BackReferenceTree(source, backslash, dh.marker, dh.opener, dh.closer, activeFlags)));
  }

  private void expectedNamedBackReferenceOpener() {
    StringJoiner joiner = new StringJoiner(" or ");
    joiner.setEmptyValue("valid name opener");
    if (source.supportsFeature(RegexFeature.DOTNET_SYNTAX_GROUP_NAME)) {
      joiner.add("'<'");
      joiner.add("'''");
    } else if (source.supportsFeature(RegexFeature.JAVA_SYNTAX_GROUP_NAME)) {
      joiner.add("'<'");
    }
    if (source.supportsFeature(RegexFeature.PERL_SYNTAX_GROUP_NAME)) {
      joiner.add("'{'");
    }
    expected(joiner.toString());
  }

  protected BackReferenceTree collect(BackReferenceTree backReference) {
    backReferences.add(backReference);
    return backReference;
  }

  protected CapturingGroupTree index(CapturingGroupTree capturingGroup) {
    capturingGroups.put(Integer.toString(capturingGroup.getGroupNumber()), capturingGroup);
    capturingGroup.getName().ifPresent(name -> capturingGroups.put(name, capturingGroup));
    return capturingGroup;
  }

  protected RegexTree parseEscapedSequence(char opener, char closer, String expected, Function<EscapedSequenceDataHolder, RegexTree> builder) {
    SourceCharacter marker = characters.getCurrent();
    characters.moveNext();

    if (!characters.currentIs(opener)) {
      expected(("'" + opener + "'"));
      return characterTree(marker);
    }
    SourceCharacter openerChar = characters.getCurrent();
    boolean atLeastOneChar = false;
    do {
      characters.moveNext();
      if (characters.isAtEnd()) {
        expected(atLeastOneChar ? ("'" + closer + "'") : expected);
        return characterTree(openerChar);
      }
      if (!atLeastOneChar && characters.currentIs(closer)) {
        expected(expected);
        return characterTree(openerChar);
      }
      atLeastOneChar = true;
    } while (!characters.currentIs(closer));
    SourceCharacter closerChar = characters.getCurrent();
    characters.moveNext();
    return builder.apply(new EscapedSequenceDataHolder(marker, openerChar, closerChar));
  }

  protected static final class EscapedSequenceDataHolder {
    private final SourceCharacter marker;
    private final SourceCharacter opener;
    private final SourceCharacter closer;

    private EscapedSequenceDataHolder(SourceCharacter marker, SourceCharacter opener, SourceCharacter closer) {
      this.marker = marker;
      this.opener = opener;
      this.closer = closer;
    }
  }

  /**
   * Parses a numerical back reference greedily, taking as many numbers as it can. The first digit is always treated
   * as a back reference, but multi digit numbers are only treated as a back reference if at least that many back
   * references exist at this point in the regex. See {@link java.util.regex.Pattern#ref(int refNum)}
   */
  protected RegexTree parseNumericalBackReference(SourceCharacter backslash) {
    SourceCharacter firstDigit = characters.getCurrent();
    SourceCharacter lastDigit = firstDigit;
    int referenceNumber = firstDigit.getCharacter() - '0';
    do {
      characters.moveNext();
      if (!characters.isAtEnd()) {
        SourceCharacter currentChar = characters.getCurrent();
        char asChar = currentChar.getCharacter();
        int newReferenceNumber = (referenceNumber * 10) + (asChar - '0');
        boolean matchingGroupExistsAtThisPoint = newReferenceNumber < groupNumber;
        if (isAsciiDigit(asChar) && matchingGroupExistsAtThisPoint) {
          lastDigit = currentChar;
          referenceNumber = newReferenceNumber;
        } else {
          break;
        }
      }
    } while (!characters.isAtEnd());
    return collect(new BackReferenceTree(source, backslash, null, firstDigit, lastDigit, activeFlags));
  }

  protected RegexTree parseOctalEscape(SourceCharacter backslash) {
    // Discard '0'
    characters.moveNext();
    char byteValue = 0;
    int i = 0;
    while (i < 3 && isOctalDigit(characters.getCurrentChar())) {
      int newValue = byteValue * 8 + characters.getCurrentChar() - '0';
      if (newValue > 0xFF) {
        break;
      }
      byteValue = (char) newValue;
      characters.moveNext();
      i++;
    }
    if (i == 0) {
      expected("octal digit");
    }
    IndexRange range = backslash.getRange().extendTo(characters.getCurrentStartIndex());
    return characterTree(new SourceCharacter(source, range, byteValue, true));
  }

  protected RegexTree parseBoundary(SourceCharacter backslash) {
    if (characters.currentIs("b{")) {
      return parseEscapedSequence(
        '{',
        '}',
        "an Unicode extended grapheme cluster",
        dh -> new BoundaryTree(source, BoundaryTree.Type.UNICODE_EXTENDED_GRAPHEME_CLUSTER, backslash.getRange().merge(dh.closer.getRange()), activeFlags));
    }
    SourceCharacter boundary = characters.getCurrent();
    characters.moveNext();
    return new BoundaryTree(source, BoundaryTree.Type.forKey(boundary.getCharacter()), backslash.getRange().merge(boundary.getRange()), activeFlags);
  }

  protected CharacterClassTree parseCharacterClass() {
    SourceCharacter openingBracket = characters.getCurrent();
    characters.moveNext();
    boolean negated = false;
    if (characters.currentIs('^')) {
      characters.moveNext();
      negated = true;
    }
    CharacterClassElementTree contents = parseCharacterClassIntersection();
    if (characters.currentIs(']')) {
      characters.moveNext();
    } else {
      expected("']'");
    }
    IndexRange range = openingBracket.getRange().extendTo(characters.getCurrentStartIndex());
    return new CharacterClassTree(source, range, openingBracket, negated, contents, activeFlags);
  }

  protected CharacterClassElementTree parseCharacterClassIntersection() {
    FlagSet characterClassFlags = activeFlags;
    List<CharacterClassElementTree> elements = new ArrayList<>();
    List<RegexToken> andOperators = new ArrayList<>();
    elements.add(parseCharacterClassUnion(true));
    while (characters.currentIs("&&")) {
      SourceCharacter firstAnd = characters.getCurrent();
      characters.moveNext();
      SourceCharacter secondAnd = characters.getCurrent();
      characters.moveNext();
      andOperators.add(new RegexToken(source, firstAnd.getRange().merge(secondAnd.getRange())));
      elements.add(parseCharacterClassUnion(false));
    }
    return combineTrees(elements, (range, items) -> new CharacterClassIntersectionTree(source, range, items, andOperators, characterClassFlags));
  }

  protected CharacterClassElementTree parseCharacterClassUnion(boolean isAtBeginning) {
    FlagSet characterClassFlags = activeFlags;
    List<CharacterClassElementTree> elements = new ArrayList<>();
    CharacterClassElementTree element = parseCharacterClassElement(isAtBeginning);
    while (element != null) {
      elements.add(element);
      element = parseCharacterClassElement(false);
    }
    if (elements.isEmpty()) {
      IndexRange range = new IndexRange(characters.getCurrentStartIndex(), characters.getCurrentStartIndex());
      return new CharacterClassUnionTree(source, range, elements, characterClassFlags);
    } else {
      return combineTrees(elements, (range, items) -> new CharacterClassUnionTree(source, range, items, characterClassFlags));
    }
  }

  @CheckForNull
  protected CharacterClassElementTree parseCharacterClassElement(boolean isAtBeginning) {
    if (characters.lookAhead(1) == ':' && source.supportsFeature(RegexFeature.POSIX_CHARACTER_CLASS)) {
      PosixCharacterClassElementTree tree = parsePosixCharacterClass();
      if (tree != null) return tree;
    }
    if (characters.isInQuotingMode() && characters.isNotAtEnd()) {
      return readCharacter();
    }
    if (characters.isAtEnd() || characters.currentIs("&&")) {
      return null;
    }
    SourceCharacter startCharacter = characters.getCurrent();
    switch (startCharacter.getCharacter()) {
      case '\\':
        RegexTree escape = parseEscapeSequence();
        if (escape.is(RegexTree.Kind.CHARACTER)) {
          return parseCharacterRange((CharacterTree) escape);
        } else if (escape instanceof CharacterClassElementTree) {
          return (CharacterClassElementTree) escape;
        } else {
          errors.add(new SyntaxError(escape, "Invalid escape sequence inside character class"));
          // Produce dummy AST and keep parsing to catch more errors.
          // The 'x' here doesn't matter because we're not going to actually use the AST when there are syntax errors.
          return characterTree(new SourceCharacter(source, escape.getRange(), 'x'));
        }
      case '[':
        return parseCharacterClass();
      case ']':
        if (isAtBeginning) {
          characters.moveNext();
          return parseCharacterRange(characterTree(startCharacter));
        } else {
          return null;
        }
      default:
        characters.moveNext();
        return parseCharacterRange(characterTree(startCharacter));
    }
  }

  @CheckForNull
  protected PosixCharacterClassElementTree parsePosixCharacterClass() {
    SourceCharacter openingBracket = characters.getCurrent();
    boolean isNegation = characters.lookAhead(2) == '^';
    Map<String, String> posixLookup = isNegation ? POSIX_CHARACTER_CLASS_NEGATION_LOOKUP : POSIX_CHARACTER_CLASS_LOOKUP;
    Optional<Map.Entry<String, String>> posixClass = posixLookup.entrySet().stream()
      .filter(posix -> characters.currentIs(posix.getKey())).findFirst();
    if (posixClass.isPresent()) {
      characters.moveNext(posixClass.get().getKey().length());
      return new PosixCharacterClassElementTree(source, openingBracket, characters.getCurrent(), isNegation, posixClass.get().getValue(), activeFlags);
    }
    return null;
  }

  protected CharacterClassElementTree parseCharacterRange(CharacterTree startCharacter) {
    if (characters.currentIs('-') && !characters.isInQuotingMode()) {
      int lookAhead = characters.lookAhead(1);
      if (lookAhead == EOF || lookAhead == ']') {
        return startCharacter;
      } else if (lookAhead == '\\') {
        characters.moveNext();
        SourceCharacter backslash = characters.getCurrent();
        RegexTree escape = parseEscapeSequence();
        if (escape.is(RegexTree.Kind.CHARACTER)) {
          return characterRange(startCharacter, (CharacterTree) escape);
        } else {
          expected("simple character", escape);
          return characterRange(startCharacter, characterTree(backslash));
        }
      } else {
        characters.moveNext();
        SourceCharacter endCharacter = characters.getCurrent();
        characters.moveNext();
        return characterRange(startCharacter, characterTree(endCharacter));
      }
    } else {
      return startCharacter;
    }
  }

  protected CharacterTree characterTree(SourceCharacter character) {
    char c1 = character.getCharacter();
    if (Character.isHighSurrogate(c1)) {
      // c1 is in the range from '\uD800' to '\uDBFF', it should be the first char of a series of two,
      // and it is one 'Supplementary Multilingual Plane' character encoded using UTF-16
      char c2 = (char) characters.getCurrentChar();
      if (c2 == '\\') {
        // skip '\\u'
        characters.moveNext(2);
        int codePoint = parseFixedAmountOfHexDigits(4);
        IndexRange newRange = new IndexRange(character.getRange().getBeginningOffset(), character.getRange().getEndingOffset() + 1);
        return new CharacterTree(character.getSource(), newRange, Character.toCodePoint(c1, (char) codePoint), true, activeFlags);
      } else if (Character.isLowSurrogate(c2)) {
        characters.moveNext();
        // c2 is in the range from '\uDC00' to '\uDFFF' it's the second part of the UTF-16 code point
        IndexRange newRange = new IndexRange(character.getRange().getBeginningOffset(), character.getRange().getEndingOffset() + 1);
        return new CharacterTree(character.getSource(), newRange, Character.toCodePoint(c1, c2), true, activeFlags);
      } else {
        LOG.warn("Couldn't parse '{}{}', two high surrogate characters in a row. Please check your encoding.", c1, c2);
      }
    }
    return new CharacterTree(source, character.getRange(), character.getCharacter(), character.isEscapeSequence(), activeFlags);
  }

  protected CharacterRangeTree characterRange(CharacterTree startCharacter, CharacterTree endCharacter) {
    IndexRange range = startCharacter.getRange().merge(endCharacter.getRange());
    CharacterRangeTree characterRange = new CharacterRangeTree(source, range, startCharacter, endCharacter, activeFlags);
    if (startCharacter.codePointOrUnit() > endCharacter.codePointOrUnit()) {
      errors.add(new SyntaxError(characterRange, "Illegal character range"));
    }
    return characterRange;
  }

  protected void expected(String expectedToken, String actual) {
    error("Expected " + expectedToken + ", but found " + actual);
  }

  protected void expected(String expectedToken, RegexSyntaxElement actual) {
    expected(expectedToken, "'" + actual.getText() + "'");
  }

  protected void expected(String expectedToken) {
    String actual = characters.isAtEnd() ? "the end of the regex" : ("'" + characters.getCurrent().getCharacter() + "'");
    expected(expectedToken, actual);
  }

  protected void error(String message) {
    IndexRange range = characters.getCurrentIndexRange();
    RegexToken offendingToken = new RegexToken(source, range);
    errors.add(new SyntaxError(offendingToken, message));
  }

  protected boolean supportsAnyOfFeatures(RegexFeature... features) {
    return Arrays.stream(features).anyMatch(source::supportsFeature);
  }

  protected static <T extends RegexSyntaxElement> T combineTrees(List<T> elements, TreeConstructor<T> treeConstructor) {
    if (elements.size() == 1) {
      return elements.get(0);
    } else {
      IndexRange range = elements.get(0).getRange().merge(elements.get(elements.size() - 1).getRange());
      return treeConstructor.construct(range, elements);
    }
  }

  protected interface TreeConstructor<T> {
    T construct(IndexRange range, List<T> elements);
  }

  protected interface GroupConstructor {
    GroupTree construct(IndexRange range, RegexTree element);
  }

  protected static boolean isAsciiDigit(int c) {
    return '0' <= c && c <= '9';
  }

  protected static boolean isOctalDigit(int c) {
    return '0' <= c && c <= '7';
  }

  protected static boolean isHexDigit(int c) {
    return ('0' <= c && c <= '9') || ('a' <= c && c <= 'f') || ('A' <= c && c <= 'F');
  }

  protected boolean isPlainTextCharacter(int c) {
    if (c == '{') {
      return supportsAnyOfFeatures(RegexFeature.UNESCAPED_CURLY_BRACKET);
    }

    switch (c) {
      case EOF:
      case '(':
      case ')':
      case '\\':
      case '*':
      case '+':
      case '?':
      case '|':
      case '[':
      case '.':
        return false;
      default:
        return true;
    }
  }

}
