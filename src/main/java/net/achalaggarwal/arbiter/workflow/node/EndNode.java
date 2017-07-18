package net.achalaggarwal.arbiter.workflow.node;

import net.achalaggarwal.arbiter.YamlElement;
import net.achalaggarwal.arbiter.workflow.Node;
import org.xembly.Directives;

public class EndNode extends Node {
  private YamlElement self = null;

  public EndNode(YamlElement self) {
    this.self = self;
  }

  @Override
  public Directives buildNode() {
    return new Directives()
      .add("end")
      .attr("name", self.getName())
      ;
  }
}
