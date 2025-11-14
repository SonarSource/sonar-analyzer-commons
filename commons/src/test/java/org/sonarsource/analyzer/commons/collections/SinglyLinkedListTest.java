/*
 * SonarSource Analyzers Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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

import java.util.Arrays;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SinglyLinkedListTest {

  @Test
  public void test() {
    PStack<Object> empty = PCollections.emptyStack();
    assertThat(empty.isEmpty()).isTrue();
    assertThat(empty).hasToString("[]");

    Object a = new Object(){
      @Override
      public String toString() {
        return "a";
      }
    };
    PStack<Object> one = empty.push(a);
    assertThat(one).hasToString("[a]");
    assertThat(one.isEmpty()).isFalse();
    assertThat(one.peek()).isSameAs(a);
    assertThat(one.pop()).isSameAs(empty);

    Object b = new Object(){
      @Override
      public String toString() {
        return "b";
      }
    };
    PStack<Object> two = one.push(b);
    assertThat(two).hasToString("[b, a]");
    assertThat(two.isEmpty()).isFalse();
    assertThat(two.peek()).isSameAs(b);
    assertThat(two.pop()).isSameAs(one);
  }

  @Test
  public void forEach() {
    List<Object> consumer = new ArrayList<>();
    PCollections.emptyStack().forEach(consumer::add);
    assertThat(consumer).isEmpty();

    Object a = new Object();
    Object b = new Object();
    PStack<Object> s = PCollections.emptyStack().push(b).push(a);
    s.forEach(consumer::add);
    assertThat(consumer).isEqualTo(Arrays.asList(a, b));
  }

  @Test
  public void equality() {
    Object a = new Object();
    Object b = new Object();
    Object c = new Object();

    PStack<Object> s1 = PCollections.emptyStack().push(b).push(a);
    PStack<Object> s2 = PCollections.emptyStack().push(b).push(a);

    assertThat(s1.equals(s1)).isTrue();
    assertThat(s1.equals("a")).isFalse();
    assertThat(s1)
      .isNotNull()
      .isEqualTo(s2)
      // twice to cover hashCode cache
      .hasSameHashCodeAs(s2)
      .hasSameHashCodeAs(s2);

    s1 = PCollections.emptyStack().push(b).push(a);
    s2 = PCollections.emptyStack().push(c).push(a);
    assertThat(s1).isNotEqualTo(s2);
  }

  @Test
  public void empty_pop() {
    PStack<Object> stack = PCollections.emptyStack();
    assertThatThrownBy(stack::pop).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void empty_peek() {
    PStack<Object> stack = PCollections.emptyStack();
    assertThatThrownBy(stack::peek).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void anyMatch() {
    PStack<Object> s = PCollections.emptyStack();
    Object a = new Object();
    Object b = new Object();
    assertThat(s.anyMatch(e -> e == a)).isFalse();
    assertThat(s.push(a).anyMatch(e -> e == a)).isTrue();
    assertThat(s.push(a).push(b).anyMatch(e -> e == a)).isTrue();
    Object c = new Object();
    assertThat(s.push(a).push(b).anyMatch(e -> e == c)).isFalse();
  }

  @Test
  public void size() {
    PStack<Object> s = PCollections.emptyStack();
    assertThat(s.size()).isZero();
    s = s.push(new Object());
    assertThat(s.size()).isEqualTo(1);
    s = s.push(new Object());
    assertThat(s.size()).isEqualTo(2);
    s = s.pop().pop();
    assertThat(s.size()).isZero();
  }

  @Test
  public void peek() {
    PStack<Object> emptyStack = PCollections.emptyStack();
    assertThatThrownBy(() -> emptyStack.peek(0)).isInstanceOf(IllegalStateException.class);

    Object a = new Object();
    PStack<Object> s = PCollections.emptyStack().push(a);
    assertThat(s.peek(0)).isEqualTo(s.peek());
    Object b = new Object();
    s = s.push(b);
    assertThat(s.peek(1)).isEqualTo(a);
    PStack<Object> finalS = s;
    assertThatThrownBy(() -> finalS.peek(2)).isInstanceOf(IllegalStateException.class);
  }

}
