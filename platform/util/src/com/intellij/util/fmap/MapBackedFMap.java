// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.fmap;

import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

final class MapBackedFMap<@NotNull K, @NotNull V> implements FMap<K, V> {

  private final @NotNull Map<K, V> myMap;

  MapBackedFMap(@NotNull Map<K, V> map) {
    assert map.size() > ArrayBackedFMap.ARRAY_THRESHOLD;
    myMap = map;
  }

  @Override
  public @NotNull FMap<K, V> plus(K key, V value) {
    if (value.equals(myMap.get(key))) {
      return this;
    }
    Map<K, V> newMap = new THashMap<>(myMap);
    newMap.put(key, value);
    return new MapBackedFMap<>(newMap);
  }

  @Override
  public @NotNull FMap<K, V> minus(K key) {
    if (!myMap.containsKey(key)) {
      return this;
    }

    THashMap<K, V> newMap = new THashMap<>(myMap);
    newMap.remove(key);

    if (newMap.size() > ArrayBackedFMap.ARRAY_THRESHOLD) {
      return new MapBackedFMap<>(newMap);
    }
    else {
      return new ArrayBackedFMap<>(newMap);
    }
  }

  @Override
  public @Nullable V get(K key) {
    return myMap.get(key);
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public int size() {
    return myMap.size();
  }

  @Override
  public @NotNull Collection<K> keys() {
    return Collections.unmodifiableSet(myMap.keySet());
  }

  @Override
  public @NotNull Map<K, V> toMap() {
    return Collections.unmodifiableMap(myMap);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MapBackedFMap<?, ?> map = (MapBackedFMap<?, ?>)o;
    return myMap.equals(map.myMap);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myMap);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[\n");
    for (Map.Entry<K, V> entry : myMap.entrySet()) {
      sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append(",\n");
    }
    sb.append("]");
    return sb.toString();
  }
}
