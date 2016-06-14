package net.achalaggarwal.arbiter.config;

import com.google.common.collect.LinkedListMultimap;
import lombok.Getter;

public class Prepare {
  @Getter
  final private LinkedListMultimap<String, String> map;

  public Prepare() {
    map = LinkedListMultimap.create();
  }

  public Prepare(Prepare prepare, Prepare... other) {
    this();

    addIfNotNull(prepare);

    for (Prepare p : other) {
      addIfNotNull(p);
    }
  }

  private void addIfNotNull(Prepare prepare) {
    if (prepare != null) {
      map.putAll(prepare.getMap());
    }
  }

  public void setProperty(String key, String value) {
    map.put(key, value);
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }
}
