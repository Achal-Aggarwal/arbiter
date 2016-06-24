package net.achalaggarwal.arbiter.workflow.node;

import net.achalaggarwal.arbiter.Action;
import net.achalaggarwal.arbiter.workflow.Node;
import org.xembly.Directives;

public class StartNode extends Node {
  private Action transitionNode = null;

  public StartNode(Action transitionNode) {
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
