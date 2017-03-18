package net.achalaggarwal.arbiter.workflow.node;

import com.google.common.collect.LinkedListMultimap;
import net.achalaggarwal.arbiter.Action;
import net.achalaggarwal.arbiter.config.ActionType;
import net.achalaggarwal.arbiter.config.Prepare;
import net.achalaggarwal.arbiter.workflow.Node;
import org.xembly.Directives;

import java.util.Map;

import static net.achalaggarwal.arbiter.util.NamedArgumentInterpolator.interpolate;

public class PrepareNode extends Node {
  private final Action action;
  private final ActionType type;

  public PrepareNode(Action action, ActionType type) {

    this.action = action;
    this.type = type;
  }

  @Override
  protected Directives buildNode() {
    return addPrepareIfPresent(type, action);
  }


  private static Directives addPrepareIfPresent(ActionType type, Action action) {
    Directives directives = new Directives();
    Prepare prepare = new Prepare(
      type.getPrepare(), action.getPrepare()
    );

    if (prepare.isEmpty()) {
      return directives;
    }

    LinkedListMultimap<String, String> p = interpolate(prepare.getMap(), action.getNamedArgs(), type.getDefaultInterpolations());

    directives.add("prepare");

    for (Map.Entry<String, String> fsOperation : p.entries()) {
      directives
        .add(fsOperation.getKey())
        .attr("path", fsOperation.getValue())
        .up();
    }

    return directives.up();
  }
}
