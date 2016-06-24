package net.achalaggarwal.arbiter.workflow.node;

import net.achalaggarwal.arbiter.workflow.Node;
import org.xembly.Directives;

import java.util.ArrayList;
import java.util.List;

public class ForkNode extends Node {
  private String name;
  List<String> paths = new ArrayList<>();

  public ForkNode(String name) {
    this.name = name;
  }

  public void addPath(String path) {
    paths.add(path);
  }

  @Override
  public Directives buildNode() {
    Directives directives = new Directives();

    directives
      .add("fork").attr("name", name);

    for (String path : paths) {
      directives.add("path")
        .attr("start", path)
        .up();
    }

    return directives.up();
  }
}
