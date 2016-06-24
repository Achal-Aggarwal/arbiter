package net.achalaggarwal.arbiter.workflow;

import org.xembly.Directives;

public abstract class Node {
  protected abstract Directives buildNode();

  public Directives appendTo(Directives directives) {
    return directives.append(buildNode());
  }
}
