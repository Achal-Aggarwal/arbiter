package net.achalaggarwal.arbiter;

import lombok.Data;

import java.util.Set;

@Data
public abstract class YamlElement {
  private String name;
  private String type;
  private Set<String> dependencies;
}
