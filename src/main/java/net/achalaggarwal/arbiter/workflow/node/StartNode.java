package net.achalaggarwal.arbiter.workflow.node;

import net.achalaggarwal.arbiter.YamlElement;
import net.achalaggarwal.arbiter.workflow.Node;
import org.xembly.Directives;

public class StartNode extends Node {
  private YamlElement transitionNode = null;

  public StartNode(YamlElement transitionNode) {
    this.transitionNode = transitionNode;
  }

  @Override
  public Directives buildNode() {
    return
      new Directives()
        .add("start").attr("to", transitionNode.getName())
        .up();
  }
}
