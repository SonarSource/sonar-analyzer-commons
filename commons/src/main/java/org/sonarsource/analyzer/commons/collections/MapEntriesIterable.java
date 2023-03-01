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

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

class MapEntriesIterable<K, V> implements Iterable<Map.Entry<K, V>> {
  private final AVLTree<K, V> map;

  public MapEntriesIterable(AVLTree<K, V> map) {
    this.map = map;
  }

  @NotNull
  @Override
  public Iterator<Map.Entry<K, V>> iterator() {
    return new MapEntriesIterator<>(new TreeIterator<>(map));
  }

  private static class MapEntriesIterator<K, V> implements Iterator<Map.Entry<K, V>> {

    private final TreeIterator<K, V> treeIterator;

    public MapEntriesIterator(TreeIterator<K, V> treeIterator) {
      this.treeIterator = treeIterator;
    }

    @Override
    public boolean hasNext() {
      return treeIterator.hasNext();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map.Entry<K, V> next() {
      AVLTree<K, V> next = treeIterator.next();
      return new AbstractMap.SimpleImmutableEntry<>((K) next.key(), (V) next.value());
    }
  }
}
