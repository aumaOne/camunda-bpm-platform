/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.impl.util;

import java.io.Serializable;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * Representation of a 2-tuple of comparable elements.
 *
 * @param <L> the comparable type of the left element of the pair
 * @param <R> the comparable type of the right element of the pair
 */
public class ComparablePair<L, R> implements Entry<L, R>, Serializable, Comparable<ComparablePair<L, R>> {
  private static final long serialVersionUID = 4954918890077093841L;

  private L left;
  private R right;

  public L getLeft() {
    return left;
  }

  public R getRight() {
    return right;
  }

  public ComparablePair(L left, R right) {
    this.left = left;
    this.right = right;
  }

  @Override
  public final L getKey() {
    return this.getLeft();
  }

  @Override
  public R getValue() {
    return this.getRight();
  }

  @Override
  public R setValue(R value) {
    throw new UnsupportedOperationException();
  }

  @Override
  @SuppressWarnings("unchecked")
  public int compareTo(ComparablePair<L, R> o) {
    if (getLeft() instanceof Comparable && getRight() instanceof Comparable) {
      int leftComparison = ((Comparable<L>) getLeft()).compareTo(o.getLeft());
      return leftComparison == 0 ? ((Comparable<R>) getRight()).compareTo(o.getRight()) : leftComparison;
    }
    throw new UnsupportedOperationException("Please provide comparable types");
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (!(obj instanceof Entry)) {
      return false;
    } else {
      Entry<?, ?> other = (Entry<?, ?>) obj;
      return Objects.equals(this.getKey(), other.getKey()) && Objects.equals(this.getValue(), other.getValue());
    }
  }

  @Override
  public int hashCode() {
    return (this.getKey() == null ? 0 : this.getKey().hashCode())
        ^ (this.getValue() == null ? 0 : this.getValue().hashCode());
  }

  @Override
  public String toString() {
    return "(" + this.getLeft() + ',' + this.getRight() + ')';
  }
}
