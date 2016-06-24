package net.achalaggarwal.arbiter.workflow.node;

import net.achalaggarwal.arbiter.Action;
import net.achalaggarwal.arbiter.workflow.Node;
import org.xembly.Directives;

public class EndNode extends Node {
  private Action self = null;

  public EndNode(Action self) {
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
