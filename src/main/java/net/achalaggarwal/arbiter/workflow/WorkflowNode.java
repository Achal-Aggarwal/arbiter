package net.achalaggarwal.arbiter.workflow;

import com.google.common.collect.Lists;
import net.achalaggarwal.arbiter.Action;
import net.achalaggarwal.arbiter.Decision;
import net.achalaggarwal.arbiter.Workflow;
import net.achalaggarwal.arbiter.YamlElement;
import net.achalaggarwal.arbiter.config.ActionType;
import net.achalaggarwal.arbiter.config.Config;
import net.achalaggarwal.arbiter.config.Credential;
import net.achalaggarwal.arbiter.config.Global;
import net.achalaggarwal.arbiter.workflow.node.ActionNode;
import net.achalaggarwal.arbiter.workflow.node.CredentialNode;
import net.achalaggarwal.arbiter.workflow.node.EndNode;
import net.achalaggarwal.arbiter.workflow.node.ForkNode;
import net.achalaggarwal.arbiter.workflow.node.GlobalNode;
import net.achalaggarwal.arbiter.workflow.node.JoinNode;
import net.achalaggarwal.arbiter.workflow.node.KillNode;
import net.achalaggarwal.arbiter.workflow.node.StartNode;
import net.achalaggarwal.arbiter.workflow.node.SwitchNode;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.DepthFirstIterator;
import org.xembly.Directives;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static net.achalaggarwal.arbiter.util.NodeGen.getActionByType;

public class WorkflowNode {
  private Workflow workflow;
  private DirectedAcyclicGraph<YamlElement, DefaultEdge> workflowGraph;
  private Config config;
  private File inputFile;


  public WorkflowNode(Workflow workflow, DirectedAcyclicGraph<YamlElement, DefaultEdge> workflowGraph, Config config, File inputFile) {
    this.workflow = workflow;
    this.workflowGraph = workflowGraph;
    this.config = config;
    this.inputFile = inputFile;
  }

  public Directives buildDOM() {
    Directives directives = new Directives();
    createRootElement(workflow, directives);

    addGlobal(config, workflow, directives);
    addCredentials(config, workflow, directives);

    YamlElement kill = getActionByType(workflowGraph, "kill");
    YamlElement end = getActionByType(workflowGraph, "end");
    YamlElement start = getActionByType(workflowGraph, "start");
    YamlElement errorHandler = workflow.getErrorHandler();
    YamlElement finalTransition = kill == null ? end : kill;
    YamlElement errorTransition = errorHandler == null ? finalTransition : errorHandler;

    DepthFirstIterator<YamlElement, DefaultEdge> iterator = new DepthFirstIterator<>(workflowGraph, start);

    while (iterator.hasNext()) {
      YamlElement a = iterator.next();
      YamlElement transition = getTransition(workflowGraph, a);
      switch (a.getType()) {
        case "start":
          new StartNode(transition).appendTo(directives);
          break;
        case "end":
          // Skip and add at the end
          break;
        case "fork":
          ForkNode forkNode  = new ForkNode(a.getName());
          for (DefaultEdge edge : workflowGraph.outgoingEdgesOf(a)) {
            forkNode.addPath(workflowGraph.getEdgeTarget(edge).getName());
          }
          forkNode.appendTo(directives);
          break;
        case "join":
          new JoinNode(a, transition).appendTo(directives);
          break;
        case "decision":
          Decision decision = (Decision) a;
          LinkedHashMap<String, String> cases = new LinkedHashMap<>();
          List<LinkedHashMap<String, String>> casesList = decision.getCases();
          for (LinkedHashMap<String, String> casesMap : casesList) {
            cases.putAll(casesMap);
          }
          new SwitchNode(a.getName(), cases, decision.getDefaultTo()).appendTo(directives);
          break;
        default:
          new ActionNode(
            (Action) a,
            getActionType(a.getType()),
            transition,
            errorTransition,
            getEnclosingForkJoinName(a, workflowGraph),
            inputFile
          ).appendTo(directives);
          break;
      }
    }
    if (kill != null) {
      new KillNode((Action) kill).appendTo(directives);
    }
    if (end != null) {
      new EndNode(end).appendTo(directives);
    }

    return directives;
  }

  private void addGlobal(Config config, Workflow workflow, Directives directives) {
    Global global = workflow.getGlobal() != null ? workflow.getGlobal() : config.getGlobal();

    new GlobalNode(global).appendTo(directives);
  }

  private void addCredentials(Config config, Workflow workflow, Directives directives) {
    ArrayList<Credential> credentials = new ArrayList<>();

    credentials.addAll(config.getCredentials());
    credentials.addAll(workflow.getCredentials());

    new CredentialNode(credentials).appendTo(directives);
  }

  /**
   * Create the root XML element
   *
   * @param workflow The workflow
   * @param directives The Xembly Directives object to which to add the root element
   */
  private void createRootElement(Workflow workflow, Directives directives) {
    directives.add("workflow-app")
      .attr("xmlns", workflow.getXmlns())
      .attr("name", workflow.getName());
  }

  /**
   * Gets the OK transition for an action
   *
   * @param workflowGraph The graph from which to get the transition
   * @param a The action for which to get the transition
   * @return The OK transition for the given action
   */
  private static YamlElement getTransition(DirectedAcyclicGraph<YamlElement, DefaultEdge> workflowGraph, YamlElement a) {
    Set<DefaultEdge> transitions = workflowGraph.outgoingEdgesOf(a);
    // end and kill nodes do not transition at all
    // forks have multiple transitions and are handled specially
    if (a.getType().equals("end") || a.getType().equals("kill") || a.getType().equals("fork")) {
      return null;
    }
    // This would be a very odd case, as only forks can have multiple transitions
    // This should be impossible, but just in case we catch it and throw an exception
    if (transitions.size() != 1)  {
      throw new RuntimeException("Multiple transitions found for action " + a.getName());
    }

    DefaultEdge transition = transitions.iterator().next();
    return workflowGraph.getEdgeTarget(transition);
  }

  /**
   * Finds the enclosing fork/join pair for an action
   * This is used to set the error transition for nodes inside a fork/join
   *
   * @param action The action for which to find the enclosing fork/join pair
   * @param workflowGraph The graph in which to find the enclosing fork/join pair
   * @return The name of the join for the fork/join pair enclosing the given action, or null if the action is not inside a fork/join
   */
  private static String getEnclosingForkJoinName(YamlElement action, DirectedAcyclicGraph<YamlElement, DefaultEdge> workflowGraph) {
    List<String> forks = new ArrayList<>();
    List<String> joins = new ArrayList<>();

    // If the action has no incoming or outgoing edges, it is definitely not inside a fork/join
    if (workflowGraph.inDegreeOf(action) == 0) {
      return null;
    }

    if (workflowGraph.outDegreeOf(action) == 0) {
      return null;
    }

    // First we traverse backwards from the given action, recording all the forks we see
    YamlElement curr = action;
    while (workflowGraph.inDegreeOf(curr) > 0) {
      DefaultEdge incoming = Lists.newArrayList(workflowGraph.incomingEdgesOf(curr)).get(0);
      curr = workflowGraph.getEdgeSource(incoming);
      if (curr.getType().equals("fork")) {
        forks.add(curr.getName());
      }
    }

    // Then we traverse forwards from the given action, recording all the joins we see
    curr = action;
    while (workflowGraph.outDegreeOf(curr) > 0) {
      DefaultEdge outgoing = Lists.newArrayList(workflowGraph.outgoingEdgesOf(curr)).get(0);
      curr = workflowGraph.getEdgeTarget(outgoing);
      if (curr.getType().equals("join")) {
        joins.add(curr.getName());
      }
    }

    // At this point we have a list of all the forks before to the given action and all the joins after the given action
    // The first fork in this list where the corresponding join was also seen is the correct enclosing fork/join
    for (String fork : forks) {
      String joinName = fork.replace("fork", "join");
      if (joins.contains(joinName)) {
        return joinName;
      }
    }

    // If there are no matching fork/join pairs, then this action is not inside a fork/join pair
    return null;
  }

  /**
   * Get the ActionType configuration from a type name
   * This caches the results to avoid searching the whole configuration repeatedly
   *
   * @param type The name of the action type for which to retrieve the configuration
   * @return An ActionType object representing the configuration for the given action type
   */
  private ActionType getActionType(String type) {
    return config.getActionTypeByName(type);
  }
}
