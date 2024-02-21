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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;

class TreeIterator<K, V> implements Iterator<AVLTree<K, V>> {

  private final Deque<AVLTree<K, V>> stack = new ArrayDeque<>();
  private AVLTree<K, V> current;

  TreeIterator(AVLTree<K, V> root) {
    current = root;
  }

  @Override
  public boolean hasNext() {
    return !stack.isEmpty() || !current.isEmpty();
  }

  @SuppressWarnings("unchecked")
  public AVLTree<K, V> next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    while (!current.isEmpty()) {
      stack.push(current);
      current = current.left();
    }
    current = stack.pop();
    AVLTree<K, V> node = current;
    current = current.right();
    return node;
  }
}
