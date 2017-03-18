package net.achalaggarwal.arbiter.util;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import net.achalaggarwal.arbiter.Action;
import net.achalaggarwal.arbiter.workflow.node.ConfigurationNode;
import net.achalaggarwal.arbiter.workflow.node.PrepareNode;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.graph.DefaultEdge;
import org.xembly.Directives;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NodeGen {
  private NodeGen() { }

  /**
   * Add elements to the inner action tag (e.g. java as opposed to the outer action tag)
   * @param properties The configuration properties for this action
   * @param configurationPosition The position within the tag where the configuration should be placed
   * @param directives The Xembly Directives object to which to add the new XML elements
   * @param interpolated Interpolated arguments from the YAML workflow definition
   * @param positional Positional arguments from the YAML workflow definition
   * @param prepareNode
   * @param preparePosition
   */
  public static void addInnerActionElements(Map<String, String> properties, int configurationPosition, Directives directives, Map<String, List<String>> interpolated, Map<String, List<String>> positional, PrepareNode prepareNode, int preparePosition) {
    Map<String, List<String>> entries = new LinkedHashMap<>();

    if (interpolated != null) {
      entries.putAll(interpolated);
    }
    if (positional != null) {
      for (String arg : positional.keySet()) {
        if (entries.containsKey(arg)) {
          entries.get(arg).addAll(positional.get(arg));
        } else {
          entries.put(arg, positional.get(arg));
        }
      }
    }

    ConfigurationNode configurationNode = new ConfigurationNode("configuration", new HashMap<String, String>(), properties);
    int i = 0;
    for (Map.Entry<String, List<String>> arg : entries.entrySet()) {
      if (preparePosition == i) {
        prepareNode.appendTo(directives);
      }
      if (configurationPosition == i) {
        configurationNode.appendTo(directives);
      }
      addKeyMultiValueElements(arg, directives);
      i++;
    }

    if (entries.size() <= configurationPosition) {
      configurationNode.appendTo(directives);
    }
  }

  /**
   * Add the elements where a given key has multiple values
   * An example of this is the arg tag
   *
   * @param entry A mapping of key to a list of values
   * @param directives The Xembly Directives object to which to add the new XML elements
   */
  private static void addKeyMultiValueElements(Map.Entry<String, List<String>> entry, Directives directives) {
    for (String value : entry.getValue()) {
      directives.add(entry.getKey())
        .set(value)
        .up();
    }
  }

  /**
   * Finds the first action in the graph of a given type
   * This is intended for use when only one action of the given type exists
   * If more than one exists the traversal order and thus the resulting action is not guaranteed
   *
   * @param workflowGraph The graph in which to find the action
   * @param type The type of action to find
   * @return The action of the given type, or null if none exists
   */
  public static Action getActionByType(DirectedAcyclicGraph<Action, DefaultEdge> workflowGraph, final String type) {
    List<Action> actionList = Lists.newArrayList(Collections2.filter(workflowGraph.vertexSet(), new Predicate<Action>() {
      @Override
      public boolean apply(Action input) {
        return input.getType().equals(type);
      }
    }));
    if (actionList.size() > 0) {
      return actionList.get(0);
    } else {
      return null;
    }
  }


}
