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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;

class TreeIterator<K, V> implements Iterator<AVLTree<K, V>> {

  private final Deque<AVLTree<K, V>> stack = new ArrayDeque<>();
  private AVLTree<K, V> current;
  private AVLTree<K, V> inBucket;

  TreeIterator(AVLTree<K, V> root) {
    current = root;
    inBucket = null;
  }

  @Override
  public boolean hasNext() {
    return !stack.isEmpty() || !current.isEmpty() || inBucket != null;
  }

  @SuppressWarnings("unchecked")
  public AVLTree<K, V> next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    if (inBucket != null) {
      var previous = inBucket;
      inBucket = inBucket.nextInBucket();
      return previous;
    }
    while (!current.isEmpty()) {
      stack.push(current);
      current = current.left();
    }
    current = stack.pop();
    AVLTree<K, V> node = current;
    current = current.right();
    inBucket = node.nextInBucket();
    return node;
  }
}
