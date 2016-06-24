package net.achalaggarwal.arbiter.workflow.node;

import net.achalaggarwal.arbiter.Action;
import net.achalaggarwal.arbiter.workflow.Node;
import org.xembly.Directives;

import java.util.LinkedHashMap;
import java.util.Map;

public class SwitchNode extends Node {
  private String name;
  private LinkedHashMap<String, String> cases;
  private String defaultTransitionName;

  public SwitchNode(final Action self, String defaultTransitionName) {
    this(
      self.getName(),
      new LinkedHashMap<String, String>(1) { {
        put(self.getActualName(), self.getOnlyIf());
      } },
      defaultTransitionName
    );
  }

  public SwitchNode(String name, LinkedHashMap<String, String> cases, String defaultTransitionName) {
    this.name = name;
    this.cases = cases;
    this.defaultTransitionName = defaultTransitionName;
  }

  @Override
  public Directives buildNode() {
    Directives directives = new Directives()
      .add("decision")
      .attr("name", name)
      .add("switch");

    for (Map.Entry<String, String> aCase : cases.entrySet()) {
      directives
        .add("case")
        .attr("to", aCase.getKey())
        .set(aCase.getValue())
        .up();
    }

    directives
      .add("default")
      .attr("to", defaultTransitionName)
      .up()
      .up()
      .up();

    return directives;
  }
}
