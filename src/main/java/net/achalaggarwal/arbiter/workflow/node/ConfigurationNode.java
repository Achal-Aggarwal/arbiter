package net.achalaggarwal.arbiter.workflow.node;

import net.achalaggarwal.arbiter.workflow.Node;
import org.xembly.Directives;

import java.util.Map;

public class ConfigurationNode extends Node {

  private final String enclosingTag;
  private final Map<String, String> enclosingTagAttr;
  private final Map<String, String> properties;

  public ConfigurationNode(String enclosingTag, Map<String, String> enclosingTagAttr, Map<String, String> properties) {
    this.enclosingTag = enclosingTag;
    this.enclosingTagAttr = enclosingTagAttr;
    this.properties = properties;
  }

  @Override
  public Directives buildNode() {
    Directives directives = new Directives();
    if (properties == null || properties.isEmpty()) {
      return directives;
    }

    directives.add(enclosingTag);
    for (String attrName : enclosingTagAttr.keySet()) {
      directives.attr(attrName, enclosingTagAttr.get(attrName));
    }

    for (Map.Entry<String, String> entry : properties.entrySet()) {
      directives.add("property")
        .add("name")
        .set(entry.getKey())
        .up()
        .add("value")
        .set(entry.getValue())
        .up()
        .up();
    }

    return directives.up();
  }
}
