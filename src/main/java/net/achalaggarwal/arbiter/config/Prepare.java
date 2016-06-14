package net.achalaggarwal.arbiter.config;

import com.google.common.collect.LinkedListMultimap;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class Prepare {
  @Getter
  private LinkedListMultimap<String, String> map = LinkedListMultimap.create();

  public Prepare(Prepare prepare, Prepare... other) {
    map.putAll(prepare.map);

    for (Prepare p : other) {
      map.putAll(p.map);
    }
  }

  public void setProperty(String key, String value) {
    map.put(key, value);
  }

  public boolean isEmpty(){
    return map.isEmpty();
  }
}
