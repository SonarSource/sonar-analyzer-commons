/*
 * SonarSource Analyzers Commons
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
package org.sonarsource.analyzer.commons.collections;

import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Persistent (functional) Stack.
 *
 * @param <E> the type of elements maintained by this stack
 */
public interface PStack<E> extends Iterable<E> {

  /**
   * @return new stack with added element
   */
  PStack<E> push(E e);

  /**
   * @return element at the top of this stack
   * @throws IllegalStateException if this stack is empty.
   */
  E peek();

  /**
   *
   * @param i - index of element to be returned, 0 means top of the stack
   * @return i-th element from top of the stack
   * @throws IllegalStateException if stack has less than i elements
   */
  E peek(int i);

  /**
   * @return new stack with removed element
   * @throws IllegalStateException if this stack is empty.
   */
  PStack<E> pop();

  /**
   * @return true if this stack contains no elements
   */
  boolean isEmpty();

  /**
   * Test given predicate on elements and return true if any of elements matches the predicate
   * @param predicate predicate to be tested
   * @return true if any of the stack elements satisfies the predicate
   */
  boolean anyMatch(Predicate<E> predicate);

  /**
   * Naive implementation has O(n) time complexity, where n is number of elements. More clever implementation could take advantage of PStack's immutability
   * @return number of elements in the stack
   */
  int size();

  /**
   * @return a string representation of this stack
   */
  @Override
  String toString();

  /**
   * @return stream of the stack's elements, starting at the top of the stack
   */
  default Stream<E> stream() {
    return StreamSupport.stream(this.spliterator(), false);
  }

  /**
   * Returns a persistent stack containing zero elements.
   *
   * @param <E> the {@code PStack}'s element type
   * @return an empty {@code PStack}
   */
  static <E> PStack<E> of() {
    return PCollections.emptyStack();
  }

  /**
   * Returns a persistent stack containing one element.
   *
   * @param <E> the {@code PStack}'s element type
   * @param e1 the single element
   * @return a {@code PStack} containing the specified element
   */
  static <E> PStack<E> of(E e1) {
    return PCollections.<E>emptyStack()
      .push(e1);
  }

  /**
   * Returns a persistent stack containing two elements, pushed left to right.
   *
   * @param <E> the {@code PStack}'s element type
   * @param e1 the first element
   * @param e2 the second element
   * @return a {@code PStack} containing the specified elements
   */
  static <E> PStack<E> of(E e1, E e2) {
    return PCollections.<E>emptyStack()
      .push(e1)
      .push(e2);
  }

  /**
   * Returns a persistent stack containing three elements, pushed left to right.
   *
   * @param <E> the {@code PStack}'s element type
   * @param e1 the first element
   * @param e2 the second element
   * @param e3 the third element
   * @return a {@code PStack} containing the specified elements
   */
  static <E> PStack<E> of(E e1, E e2, E e3) {
    return PCollections.<E>emptyStack()
      .push(e1)
      .push(e2)
      .push(e3);
  }

  /**
   * Returns a persistent stack containing four elements, pushed left to right.
   *
   * @param <E> the {@code PStack}'s element type
   * @param e1 the first element
   * @param e2 the second element
   * @param e3 the third element
   * @param e4 the fourth element
   * @return a {@code PStack} containing the specified elements
   */
  static <E> PStack<E> of(E e1, E e2, E e3, E e4) {
    return PCollections.<E>emptyStack()
      .push(e1)
      .push(e2)
      .push(e3)
      .push(e4);
  }

  /**
   * Returns a persistent stack containing five elements, pushed left to right.
   *
   * @param <E> the {@code PStack}'s element type
   * @param e1 the first element
   * @param e2 the second element
   * @param e3 the third element
   * @param e4 the fourth element
   * @param e5 the fifth element
   * @return a {@code PStack} containing the specified elements
   */
  static <E> PStack<E> of(E e1, E e2, E e3, E e4, E e5) {
    return PCollections.<E>emptyStack()
      .push(e1)
      .push(e2)
      .push(e3)
      .push(e4)
      .push(e5);
  }

  /**
   * Returns a persistent stack containing six elements, pushed left to right.
   *
   * @param <E> the {@code PStack}'s element type
   * @param e1 the first element
   * @param e2 the second element
   * @param e3 the third element
   * @param e4 the fourth element
   * @param e5 the fifth element
   * @param e6 the sixth element
   * @return a {@code PStack} containing the specified elements
   */
  static <E> PStack<E> of(E e1, E e2, E e3, E e4, E e5, E e6) {
    return PCollections.<E>emptyStack()
      .push(e1)
      .push(e2)
      .push(e3)
      .push(e4)
      .push(e5)
      .push(e6);
  }

  /**
   * Returns a persistent stack containing seven elements, pushed left to right.
   *
   * @param <E> the {@code PStack}'s element type
   * @param e1 the first element
   * @param e2 the second element
   * @param e3 the third element
   * @param e4 the fourth element
   * @param e5 the fifth element
   * @param e6 the sixth element
   * @param e7 the seventh element
   * @return a {@code PStack} containing the specified elements
   */
  static <E> PStack<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7) {
    return PCollections.<E>emptyStack()
      .push(e1)
      .push(e2)
      .push(e3)
      .push(e4)
      .push(e5)
      .push(e6)
      .push(e7);
  }

  /**
   * Returns a persistent stack containing eight elements, pushed left to right.
   *
   * @param <E> the {@code PStack}'s element type
   * @param e1 the first element
   * @param e2 the second element
   * @param e3 the third element
   * @param e4 the fourth element
   * @param e5 the fifth element
   * @param e6 the sixth element
   * @param e7 the seventh element
   * @param e8 the eighth element
   * @return a {@code PStack} containing the specified elements
   */
  static <E> PStack<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8) {
    return PCollections.<E>emptyStack()
      .push(e1)
      .push(e2)
      .push(e3)
      .push(e4)
      .push(e5)
      .push(e6)
      .push(e7)
      .push(e8);
  }

  /**
   * Returns a persistent stack containing nine elements, pushed left to right.
   *
   * @param <E> the {@code PStack}'s element type
   * @param e1 the first element
   * @param e2 the second element
   * @param e3 the third element
   * @param e4 the fourth element
   * @param e5 the fifth element
   * @param e6 the sixth element
   * @param e7 the seventh element
   * @param e8 the eighth element
   * @param e9 the ninth element
   * @return a {@code PStack} containing the specified elements
   */
  static <E> PStack<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9) {
    return PCollections.<E>emptyStack()
      .push(e1)
      .push(e2)
      .push(e3)
      .push(e4)
      .push(e5)
      .push(e6)
      .push(e7)
      .push(e8)
      .push(e9);
  }

  /**
   * Returns a persistent stack containing ten elements, pushed left to right.
   *
   * @param <E> the {@code PStack}'s element type
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
   * @return a {@code PStack} containing the specified elements
   */
  static <E> PStack<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10) {
    return PCollections.<E>emptyStack()
      .push(e1)
      .push(e2)
      .push(e3)
      .push(e4)
      .push(e5)
      .push(e6)
      .push(e7)
      .push(e8)
      .push(e9)
      .push(e10);
  }
}
