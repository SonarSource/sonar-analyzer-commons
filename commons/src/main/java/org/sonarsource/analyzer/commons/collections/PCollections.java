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
package org.sonarsource.analyzer.commons.collections;

public final class PCollections {

  private PCollections() {
  }

  /**
   * Returns a persistent set containing zero elements.
   *
   * @param <E> the {@code PSet}'s element type
   * @return an empty {@code PSet}
   */
  public static <E> PSet<E> emptySet() {
    return AVLTree.create();
  }

  /**
   * Returns a persistent map containing zero mappings.
   *
   * @param <E> the {@code PMap}'s key type
   * @param <V> the {@code PMap}'s value type
   * @return an empty {@code PMap}
   */
  public static <E, V> PMap<E, V> emptyMap() {
    return AVLTree.create();
  }

  /**
   * Returns a persistent stack containing zero elements.
   *
   * @param <E> the {@code PStack}'s element type
   * @return an empty {@code PStack}
   */
  @SuppressWarnings("unchecked")
  public static <E> PStack<E> emptyStack() {
    return SinglyLinkedList.EMPTY;
  }
}
