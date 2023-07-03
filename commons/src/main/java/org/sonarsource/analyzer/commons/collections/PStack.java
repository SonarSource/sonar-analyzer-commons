/*
 * SonarSource Analyzers Commons
 * Copyright (C) 2009-2023 SonarSource SA
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
}
