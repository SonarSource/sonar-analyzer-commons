/*
 * SonarSource Analyzers Regex Parsing Commons
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
package org.sonarsource.analyzer.commons.regex.ast;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;

public class FlagSet {

  private int mask;

  private final Map<Integer, SourceCharacter> flagCharacters;

  public FlagSet() {
    this(0);
  }

  public FlagSet(FlagSet other) {
    this.mask = other.mask;
    this.flagCharacters = new HashMap<>(other.flagCharacters);
  }

  public FlagSet(int initialFlags) {
    this.flagCharacters = new HashMap<>();
    this.mask = 0;
    add(initialFlags);
  }

  public boolean contains(int flag) {
    return (mask & flag) != 0;
  }

  /**
   * Returns the character inside the regex that was used to add the given flag to this set. This will return null if
   * the set doesn't contain the given flag or if the flag has been set from outside of the regex (i.e. as an argument
   * to Pattern.compile). Therefore this should not be used to check whether a flag is contained in this set.
   */
  @CheckForNull
  public SourceCharacter getJavaCharacterForFlag(int flag) {
    return flagCharacters.get(flag);
  }

  public void add(int flag) {
    // UNICODE_CHARACTER_CLASS implies UNICODE_CASE (both when enabling and disabling)
    if ((flag & Pattern.UNICODE_CHARACTER_CLASS) != 0) {
      mask |= Pattern.UNICODE_CASE;
    }
    mask |= flag;
  }

  public void add(int flag, SourceCharacter character) {
    add(flag);
    flagCharacters.put(flag, character);
  }

  public void addAll(FlagSet other) {
    mask |= other.mask;
    flagCharacters.putAll(other.flagCharacters);
  }

  public void removeAll(FlagSet other) {
    mask &= ~other.mask;
    flagCharacters.keySet().removeAll(other.flagCharacters.keySet());
  }

  public int getMask() {
    return mask;
  }

  public boolean isEmpty() {
    return mask == 0;
  }

}
