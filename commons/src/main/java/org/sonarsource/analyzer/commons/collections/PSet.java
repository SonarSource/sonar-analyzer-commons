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

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Persistent (functional) Set.
 *
 * @author Evgeny Mandrikov
 * @param <E> the type of elements maintained by this set
 */
public interface PSet<E> extends Iterable<E> {

  /**
   * @return new set with added element, or this if element already in the set
   */
  PSet<E> add(E e);

  /**
   * @return new set with removed element, or this if set does not contain given element
   */
  PSet<E> remove(E e);

  /**
   * @return true if this set contains the specified element
   */
  boolean contains(E e);

  /**
   * @return true if this set contains no elements
   */
  boolean isEmpty();

  /**
   * The string representation consists of a list of elements in the ascending order of hash codes.
   * If two elements have same hash code, then their relative order is arbitrary, but stable.
   *
   * @return a string representation of this set
   */
  @Override
  String toString();

  /**
   * @return stream of the set's elements
   */
  default Stream<E> stream() {
    return StreamSupport.stream(this.spliterator(), false);
  }

  /**
   * Returns a persistent set containing zero elements.
   *
   * @param <E> the {@code PSet}'s element type
   * @return an empty {@code PSet}
   */
  static <E> PSet<E> of() {
    return PCollections.emptySet();
  }

  /**
   * Returns a persistent set containing one element.
   *
   * @param <E> the {@code PSet}'s element type
   * @param e1 the single element
   * @return a {@code PSet} containing the specified element
   */
  static <E> PSet<E> of(E e1) {
    return PCollections.<E>emptySet()
      .add(e1);
  }

  /**
   * Returns a persistent set containing two elements.
   *
   * @param <E> the {@code PSet}'s element type
   * @param e1 the first element
   * @param e2 the second element
   * @return a {@code PSet} containing the specified elements
   */
  static <E> PSet<E> of(E e1, E e2) {
    return PCollections.<E>emptySet()
      .add(e1)
      .add(e2);
  }

  /**
   * Returns a persistent set containing three elements.
   *
   * @param <E> the {@code PSet}'s element type
   * @param e1 the first element
   * @param e2 the second element
   * @param e3 the third element
   * @return a {@code PSet} containing the specified elements
   */
  static <E> PSet<E> of(E e1, E e2, E e3) {
    return PCollections.<E>emptySet()
      .add(e1)
      .add(e2)
      .add(e3);
  }

  /**
   * Returns a persistent set containing four elements.
   *
   * @param <E> the {@code PSet}'s element type
   * @param e1 the first element
   * @param e2 the second element
   * @param e3 the third element
   * @param e4 the fourth element
   * @return a {@code PSet} containing the specified elements
   */
  static <E> PSet<E> of(E e1, E e2, E e3, E e4) {
    return PCollections.<E>emptySet()
      .add(e1)
      .add(e2)
      .add(e3)
      .add(e4);
  }

  /**
   * Returns a persistent set containing five elements.
   *
   * @param <E> the {@code PSet}'s element type
   * @param e1 the first element
   * @param e2 the second element
   * @param e3 the third element
   * @param e4 the fourth element
   * @param e5 the fifth element
   * @return a {@code PSet} containing the specified elements
   */
  static <E> PSet<E> of(E e1, E e2, E e3, E e4, E e5) {
    return PCollections.<E>emptySet()
      .add(e1)
      .add(e2)
      .add(e3)
      .add(e4)
      .add(e5);
  }

  /**
   * Returns a persistent set containing six elements.
   *
   * @param <E> the {@code PSet}'s element type
   * @param e1 the first element
   * @param e2 the second element
   * @param e3 the third element
   * @param e4 the fourth element
   * @param e5 the fifth element
   * @param e6 the sixth element
   * @return a {@code PSet} containing the specified elements
   */
  static <E> PSet<E> of(E e1, E e2, E e3, E e4, E e5, E e6) {
    return PCollections.<E>emptySet()
      .add(e1)
      .add(e2)
      .add(e3)
      .add(e4)
      .add(e5)
      .add(e6);
  }

  /**
   * Returns a persistent set containing seven elements.
   *
   * @param <E> the {@code PSet}'s element type
   * @param e1 the first element
   * @param e2 the second element
   * @param e3 the third element
   * @param e4 the fourth element
   * @param e5 the fifth element
   * @param e6 the sixth element
   * @param e7 the seventh element
   * @return a {@code PSet} containing the specified elements
   */
  static <E> PSet<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7) {
    return PCollections.<E>emptySet()
      .add(e1)
      .add(e2)
      .add(e3)
      .add(e4)
      .add(e5)
      .add(e6)
      .add(e7);
  }

  /**
   * Returns a persistent set containing eight elements.
   *
   * @param <E> the {@code PSet}'s element type
   * @param e1 the first element
   * @param e2 the second element
   * @param e3 the third element
   * @param e4 the fourth element
   * @param e5 the fifth element
   * @param e6 the sixth element
   * @param e7 the seventh element
   * @param e8 the eighth element
   * @return a {@code PSet} containing the specified elements
   */
  static <E> PSet<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8) {
    return PCollections.<E>emptySet()
      .add(e1)
      .add(e2)
      .add(e3)
      .add(e4)
      .add(e5)
      .add(e6)
      .add(e7)
      .add(e8);
  }

  /**
   * Returns a persistent set containing nine elements.
   *
   * @param <E> the {@code PSet}'s element type
   * @param e1 the first element
   * @param e2 the second element
   * @param e3 the third element
   * @param e4 the fourth element
   * @param e5 the fifth element
   * @param e6 the sixth element
   * @param e7 the seventh element
   * @param e8 the eighth element
   * @param e9 the ninth element
   * @return a {@code PSet} containing the specified elements
   */
  static <E> PSet<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9) {
    return PCollections.<E>emptySet()
      .add(e1)
      .add(e2)
      .add(e3)
      .add(e4)
      .add(e5)
      .add(e6)
      .add(e7)
      .add(e8)
      .add(e9);
  }

  /**
   * Returns a persistent set containing ten elements.
   *
   * @param <E> the {@code PSet}'s element type
   * @param e1 the first element
   * @param e2 the second element
   * @param e3 the third element
   * @param e4 the fourth element
   * @param e5 the fifth element
   * @param e6 the sixth element
   * @param e7 the seventh element
   * @param e8 the eighth element
   * @param e9 the ninth element
   * @param e10 the tenth element
   * @return a {@code PSet} containing the specified elements
   */
  static <E> PSet<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10) {
    return PCollections.<E>emptySet()
      .add(e1)
      .add(e2)
      .add(e3)
      .add(e4)
      .add(e5)
      .add(e6)
      .add(e7)
      .add(e8)
      .add(e9)
      .add(e10);
  }
}
