package net.achalaggarwal.arbiter.workflow.node;

import com.google.common.collect.ImmutableMap;
import net.achalaggarwal.arbiter.Action;
import net.achalaggarwal.arbiter.ConditionalKill;
import net.achalaggarwal.arbiter.YamlElement;
import net.achalaggarwal.arbiter.config.ActionType;
import net.achalaggarwal.arbiter.util.NodeGen;
import net.achalaggarwal.arbiter.workflow.Node;
import org.apache.commons.lang3.tuple.Pair;
import org.xembly.Directives;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.achalaggarwal.arbiter.util.FileArgumentInterpolator.interpolateFileVars;
import static net.achalaggarwal.arbiter.util.NamedArgumentInterpolator.interpolate;

public class ActionNode extends Node {
  public static final String ARBITER_COMMENT_TAG = "ARBITER-COMMENT";
  private Action self = null;
  private ActionType type = null;
  private YamlElement transition = null;
  private YamlElement errorTransition;
  private String enclosingForkJoinName;
  private File workflowFile;

  public ActionNode(Action self, ActionType type, YamlElement transition, YamlElement errorTransition, String enclosingForkJoinName, File workflowFile) {
    this.self = self;
    this.type = type;
    this.transition = transition;
    this.errorTransition = errorTransition;
    this.enclosingForkJoinName = enclosingForkJoinName;
    this.workflowFile = workflowFile;
  }

  @Override
  public Directives buildNode() {
    Directives directives = new Directives();

    addSwitchIfRequired(self, directives, transition);

    if (self.getComment() != null) {
      directives
        .add(ARBITER_COMMENT_TAG)
        .set(self.getComment())
        .up();
    }

    directives
      .add("action")
      .attr("name", self.getActualName());

    setAttIfNotNull(directives, "cred", self.getCred(), type.getCred());
    setAttIfNotNull(directives, "retry-max", self.getRetryMax(), type.getRetryMax());
    setAttIfNotNull(directives, "retry-interval", self.getRetryInterval(), type.getRetryInterval());

    directives.add(type.getTag());

    if (type.getXmlns() != null) {
      directives.attr("xmlns", type.getXmlns());
    }

    PrepareNode prepareNode = new PrepareNode(self, type);
    addElemsIfPresent(self, directives);

    // There is an outer action tag and an inner tag corresponding to the action type
    Map<String, List<String>> interpolated = interpolateFileVars(
      workflowFile.getParent(),
      interpolate(type.getDefaultArgs(), self.getNamedArgs(), type.getDefaultInterpolations(), self.getPositionalArgs())
    );

    Map<String, String> mergedConfigurationProperties = new HashMap<>(type.getProperties());
    if (self.getProperties() != null) {
      mergedConfigurationProperties.putAll(self.getProperties());
    }
    NodeGen.addInnerActionElements(mergedConfigurationProperties, type.getConfigurationPosition(), directives, interpolated, self.getPositionalArgs(), prepareNode, type.getPreparePosition());
    directives.up();

    String okTransitionName = self.getForceOk() != null ? self.getForceOk() : transition.getName();

    Pair<String, Directives> transitionDirectivePair = addConditionalKills(self, okTransitionName);

    directives.add("ok")
      .attr("to", transitionDirectivePair.getLeft())
      .up();

    // We allow forcing a particular error transition regardless of other considerations
    String interpolatedForceError = interpolate(self.getForceError(), ImmutableMap.of("okTransition", okTransitionName), type.getDefaultInterpolations());
    String errorTransitionName = interpolatedForceError != null ? interpolatedForceError : errorTransition.getName();
    // Find the enclosing fork/join pair
    // If an action is inside a fork/join, it should transition to the join on error
    if (enclosingForkJoinName != null) {
      errorTransitionName = interpolatedForceError != null ? interpolatedForceError : enclosingForkJoinName;
    }
    directives.add("error")
      .attr("to", errorTransitionName)
      .up();

    directives.up();

    directives.append(transitionDirectivePair.getRight());

    return directives;
  }

  private void addSwitchIfRequired(Action action, Directives directives, YamlElement transition) {
    if (action.getOnlyIf() == null) {
      return;
    }

    new SwitchNode(
      action,
      action.getForceOk() != null ? action.getForceOk() : transition.getName()
    ).appendTo(directives);
  }

  private void setAttIfNotNull(Directives directives, String attrName, Object... attrValues) {
    for (Object attrValue : attrValues) {
      if (attrValue != null) {
        directives.attr(attrName, attrValue.toString());
        return;
      }
    }
  }

  private void addElemsIfPresent(Action action, Directives directives) {
    LinkedHashMap<String, LinkedHashMap<String, String>> actionElem = action.getElem();

    if (actionElem == null) {
      return;
    }

    for (String elem : actionElem.keySet()) {
      directives
        .add(elem);

      for (String attr : actionElem.get(elem).keySet()) {
        directives.attr(attr, actionElem.get(elem).get(attr));
      }

      directives.up();
    }
  }

  private Pair<String, Directives> addConditionalKills(Action action, String defaultTransitionName) {
    Directives directives = new Directives();

    if (action.getKillIf() == null || action.getKillIf().isEmpty()) {
      return Pair.of(defaultTransitionName, directives);
    }

    int i = 0;
    LinkedHashMap<String, String> killNodesCases = new LinkedHashMap<>();
    LinkedHashMap<String, String> killNodes = new LinkedHashMap<>();
    for (ConditionalKill conditionalKill : action.getKillIf()) {
      String killNodeName = "k" + i++ + "-" + action.getActualName();
      killNodesCases.put(killNodeName, conditionalKill.getCondition());
      killNodes.put(killNodeName, conditionalKill.getMessage());
    }

    String decisionName = "ki-" + action.getActualName();
    new SwitchNode(
      decisionName,
      killNodesCases,
      defaultTransitionName
    ).appendTo(directives);

    for (final Map.Entry<String, String> killNode : killNodes.entrySet()) {
      new KillNode(
        new Action() { {
          setName(killNode.getKey());
          setProperty("message", killNode.getValue());
        } }
      ).appendTo(directives);
    }

    return Pair.of(decisionName, directives);
  }
}
