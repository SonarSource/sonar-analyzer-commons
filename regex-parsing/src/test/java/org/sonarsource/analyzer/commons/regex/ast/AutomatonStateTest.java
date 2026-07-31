/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.analyzer.commons.regex.ast;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.RegexFeature;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSuccessfulParseResult;

class AutomatonStateTest {

  @Test
  void active_flags() {
    String regex = "(?m:^)(?i:a)(?s:.)(?u:(\\\\d\\\\X))(?d:$)(?U:\\\\x{F6})(?x:[b](?=\\\\p{Lu})|\\\\1)";
    assertThat(allStates(assertSuccessfulParseResult(regex, RegexFeature.ESCAPED_CHARACTER_CLASS)).stream()
      .map(AutomatonStateTest::printClassAndFlags)
      .collect(Collectors.joining("\n")))
        .isEqualTo("""
          StartState
          SequenceTree
          NonCapturingGroupTree<flags: m:3>
          BoundaryTree<flags: m:3>
          NonCapturingGroupTree<flags: i:9>
          CharacterTree 'a' <flags: i:9>
          NonCapturingGroupTree<flags: s:15>
          DotTree<flags: s:15>
          NonCapturingGroupTree<flags: u:21>
          CapturingGroupTree<flags: u:21>
          SequenceTree<flags: u:21>
          EscapedCharacterClassTree<flags: u:21>
          MiscEscapeSequenceTree<flags: u:21>
          EndOfCapturingGroupState<flags: u:21>
          NonCapturingGroupTree<flags: d:34>
          BoundaryTree<flags: d:34>
          NonCapturingGroupTree<flags: U:40>
          CharacterTree 'ö' <flags: U:40>
          NonCapturingGroupTree<flags: x:52>
          DisjunctionTree<flags: x:52>
          SequenceTree<flags: x:52>
          CharacterClassTree<flags: x:52>
          LookAroundTree<flags: x:52>
          EscapedCharacterClassTree<flags: x:52>
          EndOfLookaroundState<flags: x:52>
          FinalState
          BackReferenceTree<flags: x:52>""");
  }

  @Test
  void active_flags_scope() {
    String regex = "(?i)a(?u:b)|[c](?-i:d)(?u)e((?-U)f)g(?U)h(?-u)i";
    assertThat(allStates(assertSuccessfulParseResult(regex)).stream()
      .map(AutomatonStateTest::printClassAndFlags)
      .collect(Collectors.joining("\n")))
        .isEqualTo("""
          StartState
          DisjunctionTree
          SequenceTree
          NonCapturingGroupTree<flags: i:3>
          CharacterTree 'a' <flags: i:3>
          NonCapturingGroupTree<flags: i:3 u:8>
          CharacterTree 'b' <flags: i:3 u:8>
          FinalState<flags: i:3 U:39>
          SequenceTree<flags: i:3>
          CharacterClassTree<flags: i:3>
          NonCapturingGroupTree
          CharacterTree 'd'\s
          NonCapturingGroupTree<flags: i:3 u:25>
          CharacterTree 'e' <flags: i:3 u:25>
          CapturingGroupTree<flags: i:3 u:25>
          SequenceTree<flags: i:3 u:25>
          NonCapturingGroupTree<flags: i:3>
          CharacterTree 'f' <flags: i:3>
          EndOfCapturingGroupState<flags: i:3 u:25>
          CharacterTree 'g' <flags: i:3 u:25>
          NonCapturingGroupTree<flags: i:3 u:25 U:39>
          CharacterTree 'h' <flags: i:3 u:25 U:39>
          NonCapturingGroupTree<flags: i:3 U:39>
          CharacterTree 'i' <flags: i:3 U:39>""");
  }

  @Test
  void active_flags_scope_with_different_types_of_groups() {
    String regex = "(?i)a(?:(?u)b)|[c](?>(?-i)d)(?u)e(?=(?-U)f)g(?U)h(?-u)i";
    assertThat(allStates(assertSuccessfulParseResult(regex, RegexFeature.ATOMIC_GROUP)).stream()
      .map(AutomatonStateTest::printClassAndFlags)
      .collect(Collectors.joining("\n")))
        .isEqualTo("""
          StartState
          DisjunctionTree
          SequenceTree
          NonCapturingGroupTree<flags: i:3>
          CharacterTree 'a' <flags: i:3>
          NonCapturingGroupTree<flags: i:3>
          SequenceTree<flags: i:3>
          NonCapturingGroupTree<flags: i:3 u:11>
          CharacterTree 'b' <flags: i:3 u:11>
          FinalState<flags: i:3 U:47>
          SequenceTree<flags: i:3>
          CharacterClassTree<flags: i:3>
          AtomicGroupTree<flags: i:3>
          SequenceTree<flags: i:3>
          NonCapturingGroupTree
          CharacterTree 'd'\s
          NonCapturingGroupTree<flags: i:3 u:31>
          CharacterTree 'e' <flags: i:3 u:31>
          LookAroundTree<flags: i:3 u:31>
          SequenceTree<flags: i:3 u:31>
          NonCapturingGroupTree<flags: i:3>
          CharacterTree 'f' <flags: i:3>
          EndOfLookaroundState<flags: i:3 u:31>
          CharacterTree 'g' <flags: i:3 u:31>
          NonCapturingGroupTree<flags: i:3 u:31 U:47>
          CharacterTree 'h' <flags: i:3 u:31 U:47>
          NonCapturingGroupTree<flags: i:3 U:47>
          CharacterTree 'i' <flags: i:3 U:47>""");
  }

  private static String printClassAndFlags(AutomatonState state) {
    String content = (state instanceof CharacterTree) ? " '" + ((CharacterTree) state).characterAsString() + "' " : "";
    return state.getClass().getSimpleName() + content + printFlags(state.activeFlags());
  }

  private static String printFlags(FlagSet flags) {
    StringBuilder out = new StringBuilder();
    if (!flags.isEmpty()) {
      out.append("<flags:");
      for (int i = 1; i <= flags.getMask(); i <<= 1) {
        SourceCharacter characterForFlag = flags.getJavaCharacterForFlag(i);
        if (characterForFlag != null) {
          out
            .append(" ")
            .append(characterForFlag.getCharacter())
            .append(":").append(characterForFlag.getRange().getBeginningOffset() + 1);
        }
      }
      out.append(">");
    }
    return out.toString();
  }

  private Collection<AutomatonState> allStates(RegexParseResult result) {
    Collection<AutomatonState> all = new LinkedHashSet<>();
    appendStates(all, result.getStartState());
    return all;
  }

  private void appendStates(Collection<AutomatonState> all, AutomatonState state) {
    if (all.add(state)) {
      state.successors().forEach(successor -> appendStates(all, successor));
    }
  }

}
