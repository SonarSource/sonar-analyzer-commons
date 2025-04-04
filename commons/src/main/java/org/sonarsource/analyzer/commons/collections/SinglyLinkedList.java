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

import java.util.Iterator;
import java.util.NoSuchElementException;
import javax.annotation.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

final class SinglyLinkedList<E> implements PStack<E> {

  private final E element;
  @Nullable
  private final SinglyLinkedList<E> next;
  private int hashCode;

  private SinglyLinkedList(E element) {
    this.element = element;
    this.next = null;
  }

  private SinglyLinkedList(E element, SinglyLinkedList<E> next) {
    this.element = element;
    this.next = next;
  }

  @Override
  public PStack<E> push(E e) {
    Objects.requireNonNull(e);
    return new SinglyLinkedList<>(e, this);
  }

  @Override
  public E peek() {
    return element;
  }

  @Override
  public E peek(int i) {
    int j = i;
    SinglyLinkedList<E> c = this;
    while (j > 0 && c != null) {
      j--;
      c = c.next;
    }
    if (c == null) {
      throw new IllegalStateException();
    }
    return c.element;
  }

  @Override
  public PStack<E> pop() {
    return next == null ? EMPTY : next;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public Iterator<E> iterator() {
    return new ListIterator<>(this);
  }

  private static class ListIterator<E> implements Iterator<E> {

    private SinglyLinkedList<E> current;

    public ListIterator(SinglyLinkedList<E> root) {
      current = root;
    }

    @Override
    public boolean hasNext() {
      return current != null;
    }

    @Override
    public E next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      SinglyLinkedList<E> c = current;
      current = current.next;
      return c.element;
    }
  }

  @Override
  public void forEach(Consumer<? super E> action) {
    SinglyLinkedList<E> c = this;
    while (c != null) {
      action.accept(c.element);
      c = c.next;
    }
  }

  @Override
  public boolean anyMatch(Predicate<E> predicate) {
    SinglyLinkedList<E> c = this;
    while (c != null) {
      if (predicate.test(c.element)) {
        return true;
      }
      c = c.next;
    }
    return false;
  }

  @Override
  public int size() {
    return 1 + (next == null ? 0 : next.size());
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      hashCode = next == null ? 0 : next.hashCode();
      hashCode = hashCode * 31 + element.hashCode();
    }
    return hashCode;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("[");
    forEach(e -> sb.append(e.toString()).append(", "));
    sb.delete(sb.length() - 2, sb.length());
    sb.append("]");
    return sb.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj instanceof SinglyLinkedList) {
      final SinglyLinkedList other = (SinglyLinkedList) obj;
      return this.element.equals(other.element)
        && Objects.equals(this.next, other.next);
    }
    return false;
  }

  static final PStack EMPTY = new PStack() {
    @SuppressWarnings("unchecked")
    @Override
    public PStack push(Object o) {
      return new SinglyLinkedList<>(o);
    }

    @Override
    public Object peek() {
      throw new IllegalStateException();
    }

    @Override
    public Object peek(int i) {
      throw new IllegalStateException();
    }

    @Override
    public PStack pop() {
      throw new IllegalStateException();
    }

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    public Iterator iterator() {
      return new Iterator() {
        @Override
        public boolean hasNext() {
          return false;
        }

        @Override
        public Object next() {
          throw new NoSuchElementException();
        }
      };
    }

    @Override
    public void forEach(Consumer action) {
      // nothing to do
    }

    @Override
    public boolean anyMatch(Predicate predicate) {
      return false;
    }

    @Override
    public int size() {
      return 0;
    }

    @Override
    public String toString() {
      return "[]";
    }
  };

}
