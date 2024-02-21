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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public final class MapEntriesIterableTest {

  @Test
  public void iteration_over_PMap() {
    Set<Pair> expectedContent = new HashSet<>(Arrays.asList(
      new Pair("foo", "bar"),
      new Pair("foo2", "bar2"),
      new Pair(1, 2),
      new Pair(new Object(), new Object())
    ));

    PMap<Object, Object> map = AVLTree.create();
    for(Pair expected: expectedContent) {
      map = map.put(expected.first, expected.second);
    }

    Set<Pair> actualContent = new HashSet<>(expectedContent.size());
    for (Map.Entry<Object, Object> entry : map.entries()) {
      actualContent.add(new Pair(entry.getKey(), entry.getValue()));
    }

    assertThat(actualContent).containsExactlyInAnyOrderElementsOf(expectedContent);
  }

  private static class Pair {
    private final Object first;
    private final Object second;

    public Pair(Object first, Object second) {
      this.first = first;
      this.second = second;
    }

    public Object getFirst() {
      return first;
    }

    public Object getSecond() {
      return second;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Pair pair = (Pair) o;
      return Objects.equals(first, pair.first) && Objects.equals(second, pair.second);
    }

    @Override
    public int hashCode() {
      return Objects.hash(first, second);
    }
  }
}
