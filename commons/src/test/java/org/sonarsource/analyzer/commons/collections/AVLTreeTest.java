/*
 * SonarSource Analyzers Commons
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
package org.sonarsource.analyzer.commons.collections;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assert.fail;

public class AVLTreeTest {

  private static final class Key {
    private final int hashCode;
    private final String toString;

    private Key(int hashCode, String toString) {
      this.hashCode = hashCode;
      this.toString = toString;
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public String toString() {
      return toString;
    }
  }

  @Test
  public void buckets() {
    Object k1 = new Key(42, "k1");
    Object k2 = new Key(42, "k2");
    Object k3 = new Key(42, "k3");
    AVLTree<Object, Object> t = AVLTree.create()
      .put(k1, "v1")
      .put(k2, "v2");

    assertThat(t)
      .as("should create bucket")
      .hasToString(" k2->v2 k1->v1");

    AVLTree<Object, Object> t2 = AVLTree.create()
      .put(k2, "v2")
      .put(k1, "v1");
    assertThat(t2)
      .as("toString depends on order of operations")
      .hasToString(" k1->v1 k2->v2");

    assertThat(t)
      .as("should compare buckets")
      .isEqualTo(t2);
    assertThat(t2)
      .as("should compare buckets")
      .isEqualTo(t);

    assertThat(t.hashCode())
      .isEqualTo(((31 * k1.hashCode()) ^ "v1".hashCode()) + ((31 * k2.hashCode()) ^ "v2".hashCode()));
    assertThat(t2)
      .as("hashCode doesn't depend on order of operations")
      .hasSameHashCodeAs(t);

    assertThat(t.get(k1))
      .isEqualTo("v1");
    assertThat(t.get(k2))
      .isEqualTo("v2");
    assertThat(t.get(k3))
      .as("not such key")
      .isNull();

    assertThat(t.put(k2, "new v2"))
      .as("should replace head of bucket")
      .hasToString(" k2->new v2 k1->v1");
    assertThat(t.put(k1, "new v1"))
      .as("should replace element of bucket")
      .hasToString(" k1->new v1 k2->v2");
    assertThat(t.put(k1, "v1"))
      .as("should not change")
      .isSameAs(t);
    assertThat(t.put(k2, "v2"))
      .as("should not change")
      .isSameAs(t);
    assertThat(t.put(k3, "v3"))
      .as("should add to bucket")
      .hasToString(" k3->v3 k2->v2 k1->v1");

    assertThat(t.remove(k2))
      .as("should remove head of bucket")
      .hasToString(" k1->v1");
    assertThat(t.remove(k1))
      .as("should remove element of bucket")
      .hasToString(" k2->v2");
    assertThat(t.remove(k1).remove(k2))
      .as("should remove bucket")
      .hasToString("");
    assertThat(t.remove(k3))
      .as("should not change")
      .isSameAs(t);

    HashMap<Object, Object> biConsumer = new HashMap<>();
    t.forEach((k, v) -> assertThat(biConsumer.put(k, v)).as("unique key-value").isNull());
    assertThat(biConsumer)
      .isEqualTo(MapBuilder.newMap()
        .put(k1, "v1")
        .put(k2, "v2")
        .build());

    HashSet<Object> consumer = new HashSet<>();
    t.forEach(k -> assertThat(consumer.add(k)).as("unique key").isTrue());
    assertThat(consumer)
      .containsOnly(k1, k2);
  }

  @Test
  public void balancing_should_preserve_buckets() {
    Object k1 = new Key(1, "k1");
    Object k2 = new Key(2, "k2");
    Object k3 = new Key(3, "k3");
    Object k4 = new Key(4, "k4");
    AVLTree<Object, Object> t = AVLTree.create()
      .put(k1, "v1")
      .put(k2, "v2")
      .put(k3, "v3");

    Object k1_1 = new Key(1, "k1_1");
    t = t.put(k1_1, "v1_1");

    t = t.put(k4, "v4");
    assertThat(t.height()).as("height after balancing").isEqualTo(3);
    assertThat(t.get(k1_1)).isEqualTo("v1_1");
  }

  /**
   * Subtraction must not be used for comparison of keys due to possibility of integer overflow,
   * this for example will be the case for sequence below, which was generated using random number generator.
   */
  @Test
  public void do_not_use_subtraction_for_comparison_of_keys() {
    Key[] keys = {
      new Key(2043979982, ""),
      new Key(-36348207, ""),
      new Key(-1864559204, ""),
      new Key(-2018458363, ""),
      new Key(-152409201, ""),
      new Key(-1786252453, ""),
      new Key(-1853960690, "")
    };
    AVLTree<Object, Object> t = AVLTree.create();
    for (Key key : keys) {
      t = t.add(key);
    }
    for (Key key : keys) {
      assertThat(t.get(key)).as("found").isNotNull();
      assertThat(t.remove(key)).as("removed").isNotSameAs(t);
    }
  }

  @Test
  public void hashCode_and_equals_should_not_depend_on_order_of_construction() {
    Object o1 = new Key(21, "o1");
    Object o2 = new Key(45, "o2");
    AVLTree<Object, Object> t1 = AVLTree.create().add(o1).add(o2);
    AVLTree<Object, Object> t2 = AVLTree.create().add(o2).add(o1);
    assertThat(t1.key()).as("shape is different").isNotEqualTo(t2.key());

    assertThat(t1)
      .hasSameHashCodeAs(t2)
      .isEqualTo(t2);
    assertThat(t2).isEqualTo(t1);

    Object o3 = new Key(0, "o3");
    AVLTree<Object, Object> t3 = t1.add(o3);
    assertThat(t1)
      .hasSameHashCodeAs(t3)
      .isNotEqualTo(t3);
    assertThat(t3).isNotEqualTo(t1);

    assertThat(t1.equals(t1)).isTrue();
    assertThat(t1.equals("a")).isFalse();
  }

  @Test
  public void test_empty() {
    AVLTree<String, String> t = AVLTree.create();
    assertThat(t).as("singleton").isSameAs(AVLTree.create());
    assertThat(t.get("anything")).isNull();
    assertThat(t.remove("anything")).isSameAs(t);
    assertThat(t).hasToString("");
    assertThat(t.hashCode()).isZero();
    assertThat(t.contains("a")).isFalse();
    assertThat(t)
      .isEqualTo(t)
      .isNotEqualTo("a");

    assertThatThrownBy(t::left).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(t::right).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(t::nextInBucket).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(t::key).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(t::value).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void test_one_element() {
    AVLTree<String, String> t0 = AVLTree.create();
    AVLTree<String, String> t1 = t0.put("1", "a");
    AVLTree<String, String> t2 = t0.put("2", "b");

    assertThat(t0).isNotSameAs(t1).isNotSameAs(t2);
    assertThat(t1).isNotSameAs(t2);

    assertThat(t0.get("1")).isNull();
    assertThat(t0.get("2")).isNull();
    assertThat(t0.get("3")).isNull();

    assertThat(t1.get("1")).isEqualTo("a");
    assertThat(t1.get("2")).isNull();
    assertThat(t2.get("3")).isNull();

    assertThat(t2.get("1")).isNull();
    assertThat(t2.get("2")).isEqualTo("b");
    assertThat(t2.get("3")).isNull();
  }

  @Test
  public void test_replace_root() {
    AVLTree<String, String> t0 = AVLTree.create();
    AVLTree<String, String> t1 = t0.put("1", "a");
    AVLTree<String, String> t2 = t1.put("1", "b");

    assertThat(t1).isNotSameAs(t2);
    assertThat(t1.get("1")).isEqualTo("a");
    assertThat(t2.get("1")).isEqualTo("b");
  }

  @Test
  public void no_change() {
    AVLTree<String, String> t0 = AVLTree.create();
    AVLTree<String, String> t1 = t0.put("1", "1");
    assertThat(t1.put("1", "1")).isSameAs(t1);
    assertThat(t1.remove("3")).isSameAs(t1);
    AVLTree<String, String> t2 = t0.put("2", "2");
    assertThat(t2.put("2", "2")).isSameAs(t2);
    assertThat(t2.remove("3")).isSameAs(t2);
  }

  @Test
  public void test() {
    List<Integer> keys = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      keys.add(i);
    }
    Collections.shuffle(keys);

    AVLTree<Integer, Object> t = AVLTree.create();
    for (Integer key : keys) {
      t = t.add(key);
      assertThat(t.add(key)).isSameAs(t);
    }
    assertThat(Counter.countSet(t)).isEqualTo(100);
    assertThat(Counter.countMap(t)).isEqualTo(100);
    assertThat(t.height())
      .isGreaterThanOrEqualTo(8)
      .isLessThanOrEqualTo(10);

    for (Integer key : keys) {
      assertThat(t.contains(key)).isTrue();
      t = t.remove(key);
      assertThat(t.remove(key)).isSameAs(t);
    }
    assertThat(Counter.countSet(t)).isZero();
    assertThat(Counter.countMap(t)).isZero();
  }

  private static class Counter<K, V> implements BiConsumer<K, V>, Consumer<K> {
    int count;

    public static <K> int countSet(PSet<K> set) {
      Counter<K, K> counter = new Counter<>();
      set.forEach(counter);
      return counter.count;
    }

    public static <K, V> int countMap(PMap<K, V> map) {
      Counter<K, V> counter = new Counter<>();
      map.forEach(counter);
      return counter.count;
    }

    @Override
    public void accept(K key, V value) {
      count++;
    }

    @Override
    public void accept(K k) {
      count++;
    }
  }

  @Test
  public void test_toArray_comparable_type() {
    AVLTree<String, String> t0 = AVLTree.create();
    AVLTree<String, String> t1 = t0.put("1", "a");
    AVLTree<String, String> t2 = t1.put("2", "b");
    assertThat(t2.toArray()).extracting("key", "value")
      .containsExactly(tuple("1", "a"), tuple("2", "b"));
  }

  private static class NotComparableString {
    private final String value;

    public NotComparableString(String value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      NotComparableString that = (NotComparableString) o;
      return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }

  @Test
  public void test_toArray_not_comparable_type() {
    AVLTree<NotComparableString, String> t0 = AVLTree.create();
    AVLTree<NotComparableString, String> t1 = t0.put(new NotComparableString("1"), "a");
    AVLTree<NotComparableString, String> t2 = t1.put(new NotComparableString("2"), "b");
    assertThat(t2.toArray()).extracting("key", "value")
      .containsExactlyInAnyOrder(tuple(new NotComparableString("1"), "a"), tuple(new NotComparableString("2"), "b"));
  }

  @Test
  public void test_NodeRenderer_toString() {
    AVLTree<String, String> t0 = AVLTree.create();
    AVLTree<String, String> t1 = t0.put("1", "a");
    AVLTree<String, String> t2 = t1.put("2", "b");

    assertThat(t2.toArray()).extracting(Object::toString)
      .containsExactly("1->a", "2->b");
  }

  @Test
  public void test_iterator_non_empty_set() {
    PSet<Integer> set = PCollections.emptySet();
    set = set.add(0);
    set = set.add(42);
    set = set.add(99);

    Set<Integer> elems = new HashSet<>();
    for (Integer elem : set) {
      elems.add(elem);
    }
    assertThat(elems).containsExactlyInAnyOrder(0, 42, 99);
  }

  @Test
  public void test_iterator_non_empty_stack() {
    PStack<Integer> stack = PCollections.emptyStack();
    stack = stack.push(0);
    stack = stack.push(42);
    stack = stack.push(99);

    Set<Integer> elems = new HashSet<>();
    for (Integer elem : stack) {
      elems.add(elem);
    }
    assertThat(elems).containsExactlyInAnyOrder(0, 42, 99);
  }


  @Test
  public void test_iterator_empty_set() {
    PSet<Integer> set = PCollections.emptySet();
    Set<Integer> elems = new HashSet<>();
    for (Integer elem : set) {
      elems.add(elem);
    }
    assertThat(elems).isEmpty();
  }

  @Test
  public void test_iterator_empty_stack() {
    PStack<Integer> stack = PCollections.emptyStack();
    Set<Integer> elems = new HashSet<>();
    for (Integer elem : stack) {
      elems.add(elem);
    }
    assertThat(elems).isEmpty();
  }

  @Test
  public void test_iterator_set_no_element_exception() {
    PSet<Integer> set = PCollections.emptySet();
    Iterator<Integer> iterator = set.iterator();
    try {
      iterator.next();
      fail("next() should throw NoSuchElementException on empty set");
    }  catch (NoSuchElementException exception) {}
  }

  @Test
  public void test_iterator_stack_no_element_exception() {
    PStack<Integer> stack = PCollections.emptyStack();
    Iterator<Integer> iterator = stack.iterator();
    try {
      iterator.next();
      fail("next() should throw NoSuchElementException on empty stack");
    }  catch (NoSuchElementException exception) {}
  }

  @Test
  public void test_iterator_stack_no_element_exception_on_non_empty() {
    PStack<Integer> stack = PCollections.emptyStack();
    stack = stack.push(42);
    Iterator<Integer> iterator = stack.iterator();
    Integer next = iterator.next();
    assertThat(next).isEqualTo(42);
    try {
      iterator.next();
      fail("next() should throw NoSuchElementException on empty stack");
    }  catch (NoSuchElementException exception) {}
  }

  @Test
  public void test_empty_keyset() {
    assertThat(PCollections.emptyMap().keySet()).isEmpty();
  }

  @Test
  public void test_keyset() {
    PMap<String, Integer> map = PCollections.emptyMap();
    map = map.put("A", 3).put("B", 4);
    PSet<String> set = map.keySet();
    assertThat(set).containsExactlyInAnyOrder("B", "A");
  }

  @Test
  public void test_empty_set_stream() {
    assertThat(PCollections.emptySet().stream()).isEmpty();
  }

  @Test
  public void test_set_stream() {
    PSet<Integer> set = PCollections.emptySet();
    set = set.add(1).add(2).add(3);
    Stream<Integer> stream = set.stream();
    assertThat(stream).containsExactlyInAnyOrder(1, 2, 3);
  }

  @Test
  public void test_empty_stack_stream() {
    assertThat(PCollections.emptyStack().stream()).isEmpty();
  }

  @Test
  public void test_stack_stream() {
    PStack<Integer> stack = PCollections.emptyStack();
    stack = stack.push(1).push(2).push(3);
    Stream<Integer> stream = stack.stream();
    assertThat(stream).containsExactly(3, 2, 1);
  }

  @Test
  public void test_not_equal_but_same_hashcode_iteration() {
    var set = PSet.of(
      new Weird(0),
      new Weird(0)
    );

    int count = 0;
    for (var ignored : set) {
      count++;
    }

    assertThat(count).isEqualTo(2);
  }

  @Test
  public void test_not_equal_but_same_hashcode_forEach() {
    var set = PSet.of(
      new Weird(0),
      new Weird(0)
    );

    var count = new AtomicInteger(0);
    set.forEach(ignored -> count.incrementAndGet());

    assertThat(count.get()).isEqualTo(2);
  }

  @Test
  public void test_not_equal_but_same_hashcode_iteration_many() {
    var set = PSet.of(
      new Weird(0),
      new Weird(42),
      new Weird(0),
      new Weird(1),
      new Weird(42)
    );

    int count = 0;
    for (var ignored : set) {
      count++;
    }

    assertThat(count).isEqualTo(5);
  }

  @Test
  public void test_not_equal_but_same_hashcode_forEach_many() {
    var set = PSet.of(
      new Weird(0),
      new Weird(42),
      new Weird(0),
      new Weird(1),
      new Weird(42)
    );

    var count = new AtomicInteger(0);
    set.forEach(ignored -> count.incrementAndGet());

    assertThat(count.get()).isEqualTo(5);
  }

  static class Weird {
    private final int hashCode;

    public Weird(int hashCode) {
      this.hashCode = hashCode;
    }

    @Override
    public boolean equals(Object obj) {
      return false;
    }

    @Override
    public int hashCode() {
      return hashCode;
    }
  }
}
