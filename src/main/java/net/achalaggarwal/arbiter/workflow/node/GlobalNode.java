package net.achalaggarwal.arbiter.workflow.node;

import net.achalaggarwal.arbiter.config.Global;
import net.achalaggarwal.arbiter.util.NodeGen;
import net.achalaggarwal.arbiter.workflow.Node;
import org.xembly.Directives;

import java.util.HashMap;
import java.util.List;

public class GlobalNode extends Node {
  private Global self;

  public GlobalNode(Global self) {
    this.self = self;
  }

  @Override
  public Directives buildNode() {
    Directives directives = new Directives();

    if (self == null) {
      return directives;
    }

    directives.add("global");

    NodeGen.addInnerActionElements(
      self.getProperties(),
      self.getConfigurationPosition(),
      directives,
      new HashMap<String, List<String>>(),
      self.getDefaultArgs()
    );

    return directives.up();
  }
}
