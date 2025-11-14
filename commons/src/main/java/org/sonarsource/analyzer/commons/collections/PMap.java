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

import java.util.Map;
import javax.annotation.Nullable;
import java.util.function.BiConsumer;

/**
 * Persistent (functional) Map.
 *
 * @author Evgeny Mandrikov
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public interface PMap<K, V> {

  /**
   * @return new map with added key-value pair, or this if map already contains given key-value pair
   */
  PMap<K, V> put(K key, V value);

  /**
   * @return new map with removed key, or this if map does not contain given key
   */
  PMap<K, V> remove(K key);

  /**
   * @return value associated with given key, or null if not found
   */
  @Nullable
  V get(K key);

  /**
   * Performs the given action for each entry in this map until all entries have been processed or the action throws an exception.
   */
  void forEach(BiConsumer<K, V> action);

  /**
   * @return true if this map contains no elements
   */
  boolean isEmpty();

  /**
   * The string representation consists of a list of key-value mappings in the ascending order of hash codes of keys.
   * If two keys have same hash code, then their relative order is arbitrary, but stable.
   *
   * @return a string representation of this map
   */
  @Override
  String toString();

  Iterable<Map.Entry<K, V>> entries();

  /**
   * @return a set view of the keys contained in the map.
   */
  PSet<K> keySet();

  /**
   * Returns a persistent map containing zero mappings.
   *
   * @param <K> the {@code PMap}'s key type
   * @param <V> the {@code PMap}'s value type
   * @return an empty {@code PMap}
   */
  static <K, V> PMap<K, V> of() {
    return PCollections.emptyMap();
  }

  /**
   * Returns a persistent map containing a single mapping.
   *
   * @param <K> the {@code PMap}'s key type
   * @param <V> the {@code PMap}'s value type
   * @param k1 the mapping's key
   * @param v1 the mapping's value
   * @return a {@code PMap} containing the specified mapping
   */
  static <K, V> PMap<K, V> of(K k1, V v1) {
    return PCollections.<K, V>emptyMap()
      .put(k1, v1);
  }

  /**
   * Returns a persistent map containing two mappings.
   *
   * @param <K> the {@code PMap}'s key type
   * @param <V> the {@code PMap}'s value type
   * @param k1 the first mapping's key
   * @param v1 the first mapping's value
   * @param k2 the second mapping's key
   * @param v2 the second mapping's value
   * @return a {@code PMap} containing the specified mappings
   */
  static <K, V> PMap<K, V> of(K k1, V v1, K k2, V v2) {
    return PCollections.<K, V>emptyMap()
      .put(k1, v1)
      .put(k2, v2);
  }

  /**
   * Returns a persistent map containing three mappings.
   *
   * @param <K> the {@code PMap}'s key type
   * @param <V> the {@code PMap}'s value type
   * @param k1 the first mapping's key
   * @param v1 the first mapping's value
   * @param k2 the second mapping's key
   * @param v2 the second mapping's value
   * @param k3 the third mapping's key
   * @param v3 the third mapping's value
   * @return a {@code PMap} containing the specified mappings
   */
  static <K, V> PMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3) {
    return PCollections.<K, V>emptyMap()
      .put(k1, v1)
      .put(k2, v2)
      .put(k3, v3);
  }

  /**
   * Returns a persistent map containing four mappings.
   *
   * @param <K> the {@code PMap}'s key type
   * @param <V> the {@code PMap}'s value type
   * @param k1 the first mapping's key
   * @param v1 the first mapping's value
   * @param k2 the second mapping's key
   * @param v2 the second mapping's value
   * @param k3 the third mapping's key
   * @param v3 the third mapping's value
   * @param k4 the fourth mapping's key
   * @param v4 the fourth mapping's value
   * @return a {@code PMap} containing the specified mappings
   */
  static <K, V> PMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
    return PCollections.<K, V>emptyMap()
      .put(k1, v1)
      .put(k2, v2)
      .put(k3, v3)
      .put(k4, v4);
  }

  /**
   * Returns a persistent map containing five mappings.
   *
   * @param <K> the {@code PMap}'s key type
   * @param <V> the {@code PMap}'s value type
   * @param k1 the first mapping's key
   * @param v1 the first mapping's value
   * @param k2 the second mapping's key
   * @param v2 the second mapping's value
   * @param k3 the third mapping's key
   * @param v3 the third mapping's value
   * @param k4 the fourth mapping's key
   * @param v4 the fourth mapping's value
   * @param k5 the fifth mapping's key
   * @param v5 the fifth mapping's value
   * @return a {@code PMap} containing the specified mappings
   */
  static <K, V> PMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
    return PCollections.<K, V>emptyMap()
      .put(k1, v1)
      .put(k2, v2)
      .put(k3, v3)
      .put(k4, v4)
      .put(k5, v5);
  }

  /**
   * Returns a persistent map containing six mappings.
   *
   * @param <K> the {@code PMap}'s key type
   * @param <V> the {@code PMap}'s value type
   * @param k1 the first mapping's key
   * @param v1 the first mapping's value
   * @param k2 the second mapping's key
   * @param v2 the second mapping's value
   * @param k3 the third mapping's key
   * @param v3 the third mapping's value
   * @param k4 the fourth mapping's key
   * @param v4 the fourth mapping's value
   * @param k5 the fifth mapping's key
   * @param v5 the fifth mapping's value
   * @param k6 the sixth mapping's key
   * @param v6 the sixth mapping's value
   * @return a {@code PMap} containing the specified mappings
   */
  static <K, V> PMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
    return PCollections.<K, V>emptyMap()
      .put(k1, v1)
      .put(k2, v2)
      .put(k3, v3)
      .put(k4, v4)
      .put(k5, v5)
      .put(k6, v6);
  }

  /**
   * Returns a persistent map containing seven mappings.
   *
   * @param <K> the {@code PMap}'s key type
   * @param <V> the {@code PMap}'s value type
   * @param k1 the first mapping's key
   * @param v1 the first mapping's value
   * @param k2 the second mapping's key
   * @param v2 the second mapping's value
   * @param k3 the third mapping's key
   * @param v3 the third mapping's value
   * @param k4 the fourth mapping's key
   * @param v4 the fourth mapping's value
   * @param k5 the fifth mapping's key
   * @param v5 the fifth mapping's value
   * @param k6 the sixth mapping's key
   * @param v6 the sixth mapping's value
   * @param k7 the seventh mapping's key
   * @param v7 the seventh mapping's value
   * @return a {@code PMap} containing the specified mappings
   */
  static <K, V> PMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7) {
    return PCollections.<K, V>emptyMap()
      .put(k1, v1)
      .put(k2, v2)
      .put(k3, v3)
      .put(k4, v4)
      .put(k5, v5)
      .put(k6, v6)
      .put(k7, v7);
  }

  /**
   * Returns a persistent map containing eight mappings.
   *
   * @param <K> the {@code PMap}'s key type
   * @param <V> the {@code PMap}'s value type
   * @param k1 the first mapping's key
   * @param v1 the first mapping's value
   * @param k2 the second mapping's key
   * @param v2 the second mapping's value
   * @param k3 the third mapping's key
   * @param v3 the third mapping's value
   * @param k4 the fourth mapping's key
   * @param v4 the fourth mapping's value
   * @param k5 the fifth mapping's key
   * @param v5 the fifth mapping's value
   * @param k6 the sixth mapping's key
   * @param v6 the sixth mapping's value
   * @param k7 the seventh mapping's key
   * @param v7 the seventh mapping's value
   * @param k8 the eighth mapping's key
   * @param v8 the eighth mapping's value
   * @return a {@code PMap} containing the specified mappings
   */
  static <K, V> PMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8) {
    return PCollections.<K, V>emptyMap()
      .put(k1, v1)
      .put(k2, v2)
      .put(k3, v3)
      .put(k4, v4)
      .put(k5, v5)
      .put(k6, v6)
      .put(k7, v7)
      .put(k8, v8);
  }

  /**
   * Returns a persistent map containing nine mappings.
   *
   * @param <K> the {@code PMap}'s key type
   * @param <V> the {@code PMap}'s value type
   * @param k1 the first mapping's key
   * @param v1 the first mapping's value
   * @param k2 the second mapping's key
   * @param v2 the second mapping's value
   * @param k3 the third mapping's key
   * @param v3 the third mapping's value
   * @param k4 the fourth mapping's key
   * @param v4 the fourth mapping's value
   * @param k5 the fifth mapping's key
   * @param v5 the fifth mapping's value
   * @param k6 the sixth mapping's key
   * @param v6 the sixth mapping's value
   * @param k7 the seventh mapping's key
   * @param v7 the seventh mapping's value
   * @param k8 the eighth mapping's key
   * @param v8 the eighth mapping's value
   * @param k9 the ninth mapping's key
   * @param v9 the ninth mapping's value
   * @return a {@code PMap} containing the specified mappings
   */
  static <K, V> PMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9) {
    return PCollections.<K, V>emptyMap()
      .put(k1, v1)
      .put(k2, v2)
      .put(k3, v3)
      .put(k4, v4)
      .put(k5, v5)
      .put(k6, v6)
      .put(k7, v7)
      .put(k8, v8)
      .put(k9, v9);
  }

  /**
   * Returns a persistent map containing ten mappings.
   *
   * @param <K> the {@code PMap}'s key type
   * @param <V> the {@code PMap}'s value type
   * @param k1 the first mapping's key
   * @param v1 the first mapping's value
   * @param k2 the second mapping's key
   * @param v2 the second mapping's value
   * @param k3 the third mapping's key
   * @param v3 the third mapping's value
   * @param k4 the fourth mapping's key
   * @param v4 the fourth mapping's value
   * @param k5 the fifth mapping's key
   * @param v5 the fifth mapping's value
   * @param k6 the sixth mapping's key
   * @param v6 the sixth mapping's value
   * @param k7 the seventh mapping's key
   * @param v7 the seventh mapping's value
   * @param k8 the eighth mapping's key
   * @param v8 the eighth mapping's value
   * @param k9 the ninth mapping's key
   * @param v9 the ninth mapping's value
   * @param k10 the tenth mapping's key
   * @param v10 the tenth mapping's value
   * @return a {@code PMap} containing the specified mappings
   */
  static <K, V> PMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9, K k10, V v10) {
    return PCollections.<K, V>emptyMap()
      .put(k1, v1)
      .put(k2, v2)
      .put(k3, v3)
      .put(k4, v4)
      .put(k5, v5)
      .put(k6, v6)
      .put(k7, v7)
      .put(k8, v8)
      .put(k9, v9)
      .put(k10, v10);
  }
}
