package net.achalaggarwal.arbiter.workflow.node;

import net.achalaggarwal.arbiter.Action;
import net.achalaggarwal.arbiter.workflow.Node;
import org.xembly.Directives;

public class KillNode extends Node {
  private Action self;
  private String message;


  public KillNode(Action self) {
    this.self = self;
    this.message = self.getNamedArgs().get("message");
  }

  @Override
  public Directives buildNode() {
    return new Directives()
      .add("kill").attr("name", self.getName())
      .add("message").set(message)
      .up()
      .up();
  }
}
