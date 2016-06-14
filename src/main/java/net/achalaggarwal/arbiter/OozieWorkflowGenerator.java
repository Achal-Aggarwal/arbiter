/*
 * Copyright 2015-2016 Etsy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * -----------------------------------------------------------------------
 *
 * This file has been modified from its original licensed form.
 * Modifications are Copyright (C) 2016 Achal Aggarwal (achalaggarwal.net).
 */

package net.achalaggarwal.arbiter;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import net.achalaggarwal.arbiter.config.*;
import net.achalaggarwal.arbiter.exception.WorkflowGraphException;
import net.achalaggarwal.arbiter.util.GraphvizGenerator;
import net.achalaggarwal.arbiter.workflow.WorkflowGraphBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.DepthFirstIterator;
import org.w3c.dom.Document;
import org.xembly.Directives;
import org.xembly.ImpossibleModificationException;
import org.xembly.Xembler;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import static net.achalaggarwal.arbiter.util.FileArgumentInterpolator.interpolateFileVars;
import static net.achalaggarwal.arbiter.util.NamedArgumentInterpolator.interpolate;

/**
 * Generates Oozie workflows from Arbiter workflows
 *
 * @author Andrew Johnson
 */
public class OozieWorkflowGenerator {
    private static final Logger LOG = Logger.getLogger(OozieWorkflowGenerator.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss");

    private Config config;
    private Map<String, ActionType> actionTypeCache;

    public OozieWorkflowGenerator(Config config) {
        this.config = config;
        actionTypeCache = new HashMap<>();
    }

    /**
     * Generate Oozie workflows from Arbiter workflows
     * @param workflows The workflows to convert
     * @param generateGraphviz Indicate if Graphviz graphs should be generated for workflows
     * @param graphvizFormat The format in which Graphviz graphs should be generated if enabled
     */
    public void generateOozieWorkflows(Map<File, Workflow> workflows, boolean generateGraphviz, String graphvizFormat) throws IOException, ParserConfigurationException, TransformerException {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        Date currentDate = new Date();
        String currentDateString = DATE_FORMAT.format(currentDate);

        for (File inputFile : workflows.keySet()) {
            String parentDir = inputFile.getParentFile().getAbsolutePath();
            String inputFileName = inputFile.getName();
            inputFileName = inputFileName.substring(0, inputFileName.lastIndexOf("."));
            String outputFileAbsolutePath = new File(parentDir, inputFileName).getAbsolutePath();

            File dotFilesBaseDir = new File(parentDir, "dot");
            FileUtils.forceMkdir(dotFilesBaseDir);
            String outputDotFilesAbsolutePath = new File(dotFilesBaseDir, inputFileName).getAbsolutePath();

            Workflow workflow = workflows.get(inputFile);


            DirectedAcyclicGraph<Action, DefaultEdge> workflowGraph = null;

            try {
                workflowGraph = WorkflowGraphBuilder.buildWorkflowGraph(workflow, config, outputDotFilesAbsolutePath, generateGraphviz, graphvizFormat);
            } catch (WorkflowGraphException w) {
                LOG.error("Unable to generate workflow", w);
                System.exit(1);
            }

            if (generateGraphviz) {
                GraphvizGenerator.generateGraphviz(workflowGraph, outputDotFilesAbsolutePath + ".dot", graphvizFormat);
            }

            Document xmlDoc = builder.newDocument();

            Directives directives = new Directives();
            createRootElement(workflow, directives);

            addGlobal(config, workflow, directives);
            addCredentials(config, workflow, directives);

            Action kill = getActionByType(workflowGraph, "kill");
            Action end = getActionByType(workflowGraph, "end");
            Action start = getActionByType(workflowGraph, "start");
            Action errorHandler = workflow.getErrorHandler();
            Action finalTransition = kill == null ? end : kill;

            Action errorTransition = errorHandler == null ? (kill == null ? end : kill) : errorHandler;
            DepthFirstIterator<Action, DefaultEdge> iterator = new DepthFirstIterator<>(workflowGraph, start);

            while (iterator.hasNext()) {
                Action a = iterator.next();
                Action transition = getTransition(workflowGraph, a);
                switch (a.getType()) {
                    case "start":
                        if (transition == null) {
                            throw new RuntimeException("No transition found for start action");
                        }
                        directives.add("start")
                                .attr("to", transition.getName())
                                .up();
                        break;
                    case "end":
                        // Skip and add at the end
                        break;
                    case "fork":
                        directives.add("fork")
                                .attr("name", a.getName());
                        for (DefaultEdge edge : workflowGraph.outgoingEdgesOf(a)) {
                            Action target = workflowGraph.getEdgeTarget(edge);
                            directives.add("path")
                                    .attr("start", target.getName())
                                    .up();
                        }
                        directives.up();
                        break;
                    case "join":
                        if (transition == null) {
                            throw new RuntimeException(String.format("No transition found for join action %s", a.getName()));
                        }
                        directives.add("join")
                                .attr("name", a.getName())
                                .attr("to", transition.getName())
                                .up();
                        break;
                    default:
                        createActionElement(inputFile, a, workflowGraph, transition, a.equals(errorHandler) ? finalTransition : errorTransition, directives);
                        break;
                }
            }
            if (kill != null) {
                addKillNode(directives, kill.getName(), kill.getNamedArgs().get("message"));
            }
            if (end != null) {
                directives.add("end")
                        .attr("name", end.getName())
                        .up();
            }

            try {
                new Xembler(directives).apply(xmlDoc);
            } catch (ImpossibleModificationException e) {
                throw new RuntimeException(e);
            }
            writeDocument(outputFileAbsolutePath, xmlDoc, transformer, workflow.getName(), currentDateString);
        }
    }

    private void addKillNode(Directives directives, String name, String message) {
        directives.add("kill")
                .attr("name", name)
                .add("message")
                .set(message)
                .up()
                .up();
    }

    private void addGlobal(Config config, Workflow workflow, Directives directives) {
        Global global = workflow.getGlobal() != null ? workflow.getGlobal() : config.getGlobal();

        if (global == null) {
            return;
        }

        directives.add("global");

        addInnerActionElements(
          global.getProperties(),
          global.getConfigurationPosition(),
          directives,
          new HashMap<String, List<String>>(),
          global.getDefaultArgs()
        );

        directives.up();
    }

    private void addCredentials(Config config, Workflow workflow, Directives directives) {
        ArrayList<Credential> credentials = new ArrayList<>();

        credentials.addAll(config.getCredentials());
        credentials.addAll(workflow.getCredentials());

        addCredentials(credentials, directives);
    }

    private void addCredentials(List<Credential> credentials, Directives directives) {
        directives.add("credentials");

        for (final Credential credential : credentials) {
            HashMap<String, String> attributes = new HashMap<String, String>() { {
                put("name", credential.getName());
                put("type", credential.getType());
            } };

            createConfigurationElement("credential", attributes, credential.getProperties(), directives);
        }

        directives.up();
    }

    /**
     * Add the XML element for an action
     *
     * @param inputFile
     * @param action The action for which to add the element
     * @param workflowGraph The full workflow graph
     * @param transition The OK transition for this action
     * @param errorTransition The error transition for this action if it is not inside a fork/join pair
     * @param directives The Xembly Directives object to which to add the new XML elements
     */
    private void createActionElement(File inputFile, Action action, DirectedAcyclicGraph<Action, DefaultEdge> workflowGraph, Action transition, Action errorTransition, Directives directives) {
        ActionType type = getActionType(action.getType());

        addSwitchIfRequired(action, directives, transition);

        directives.add("action")
                .attr("name", action.getActualName());

        setAttIfNotNull(directives, "cred", action.getCred(), type.getCred());
        setAttIfNotNull(directives, "retry-max", action.getRetryMax(), type.getRetryMax());
        setAttIfNotNull(directives, "retry-interval", action.getRetryInterval(), type.getRetryInterval());

        directives.add(type.getTag());

        if (type.getXmlns() != null) {
            directives.attr("xmlns", type.getXmlns());
        }

        addPrepareIfPresent(type, action, directives);

        addElemsIfPresent(action, directives);

        // There is an outer action tag and an inner tag corresponding to the action type
        Map<String, List<String>> interpolated = interpolateFileVars(
          inputFile.getParent(),
          interpolate(type.getDefaultArgs(), action.getNamedArgs(), type.getDefaultInterpolations(), action.getPositionalArgs())
        );

        Map<String, String> mergedConfigurationProperties = new HashMap<>(type.getProperties());
        if (action.getProperties() != null) {
            mergedConfigurationProperties.putAll(action.getProperties());
        }
        addInnerActionElements(mergedConfigurationProperties, type.getConfigurationPosition(), directives, interpolated, action.getPositionalArgs());
        directives.up();

        String okTransitionName = action.getForceOk() != null ? action.getForceOk() : transition.getName();

        Pair<String, Directives> transitionDirectivePair = addConditionalKills(action, okTransitionName);

        directives.add("ok")
                .attr("to", transitionDirectivePair.getLeft())
                .up();

        // We allow forcing a particular error transition regardless of other considerations
        String interpolatedForceError = interpolate(action.getForceError(), ImmutableMap.of("okTransition", okTransitionName), type.getDefaultInterpolations());
        String errorTransitionName = interpolatedForceError != null ? interpolatedForceError : errorTransition.getName();
        // Find the enclosing fork/join pair
        // If an action is inside a fork/join, it should transition to the join on error
        String enclosingJoinName = getEnclosingForkJoinName(action, workflowGraph);
        if (enclosingJoinName != null) {
            errorTransitionName = interpolatedForceError != null ? interpolatedForceError : enclosingJoinName;
        }
        directives.add("error")
                .attr("to", errorTransitionName)
                .up();

        directives.up();

        directives.append(transitionDirectivePair.getRight());
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

    private void setAttIfNotNull(Directives directives, String attrName, Object... attrValues) {
        for (Object attrValue : attrValues) {
            if (attrValue != null) {
                directives.attr(attrName, attrValue.toString());
                return;
            }
        }
    }

    private void addPrepareIfPresent(ActionType type, Action action, Directives directives) {
        Prepare prepare = new Prepare(
          type.getPrepare(), action.getPrepare()
        );

        if (prepare.isEmpty()) {
            return;
        }

        LinkedListMultimap<String, String> p = interpolate(prepare.getMap(), action.getNamedArgs(), type.getDefaultInterpolations());

        directives.add("prepare");

        for (Map.Entry<String, String> fsOperation : p.entries()) {
            directives
              .add(fsOperation.getKey())
              .attr("path", fsOperation.getValue())
              .up();
        }

        directives.up();
    }

    private void addSwitchIfRequired(Action action, Directives directives, Action transition) {
        if (action.getOnlyIf() == null) {
            return;
        }

        String defaultTransitionName = action.getForceOk() != null ? action.getForceOk() : transition.getName();

        directives
          .add("decision")
          .attr("name", action.getName())
          .add("switch")
          .add("case")
          .attr("to", action.getActualName())
          .set(action.getOnlyIf())
          .up()
          .add("default")
          .attr("to", defaultTransitionName)
          .up()
          .up()
          .up();

    }

    private Pair<String, Directives> addConditionalKills(Action action, String defaultTransitionName) {
        Directives directives = new Directives();

        if (action.getKillIf() == null || action.getKillIf().isEmpty()) {
            return Pair.of(defaultTransitionName, directives);
        }

        String decisionName = "ki-" + action.getActualName();
        directives
          .add("decision")
          .attr("name", decisionName)
          .add("switch");

        int i = 0;
        LinkedHashMap<String, String> killNodes = new LinkedHashMap<>();
        for (ConditionalKill conditionalKill : action.getKillIf()) {
            String killNodeName = "k" + i++ + "-" + action.getActualName();
            killNodes.put(killNodeName, conditionalKill.getMessage());
            directives
              .add("case")
              .attr("to", killNodeName)
              .set(conditionalKill.getCondition())
              .up();
        }

        directives
          .add("default")
          .attr("to", defaultTransitionName)
          .up()
          .up()
          .up();

        for (Map.Entry<String, String> killNode : killNodes.entrySet()) {
            addKillNode(directives, killNode.getKey(), killNode.getValue());
        }


        return Pair.of(decisionName, directives);
    }

    /**
     * Finds the enclosing fork/join pair for an action
     * This is used to set the error transition for nodes inside a fork/join
     *
     * @param action The action for which to find the enclosing fork/join pair
     * @param workflowGraph The graph in which to find the enclosing fork/join pair
     * @return The name of the join for the fork/join pair enclosing the given action, or null if the action is not inside a fork/join
     */
    private String getEnclosingForkJoinName(Action action, DirectedAcyclicGraph<Action, DefaultEdge> workflowGraph) {
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
        Action curr = action;
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
     * Add elements to the inner action tag (e.g. java as opposed to the outer action tag)
     *
     * @param properties The configuration properties for this action
     * @param configurationPosition The position within the tag where the configuration should be placed
     * @param directives The Xembly Directives object to which to add the new XML elements
     * @param interpolated Interpolated arguments from the YAML workflow definition
     * @param positional Positional arguments from the YAML workflow definition
     */
    private void addInnerActionElements(Map<String, String> properties, int configurationPosition, Directives directives, Map<String, List<String>> interpolated, Map<String, List<String>> positional) {
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

        int i = 0;
        for (Map.Entry<String, List<String>> arg : entries.entrySet()) {
            if (configurationPosition == i++) {
                createConfigurationElement("configuration", new HashMap<String, String>(), properties, directives);
            }
            addKeyMultiValueElements(arg, directives);
        }

        if (entries.size() <= configurationPosition) {
            createConfigurationElement("configuration", new HashMap<String, String>(), properties, directives);
        }
    }

    /**
     * Add the configuration element for a workflow action
     *
     * @param properties The configuration properties
     * @param directives The Xembly Directives object to which to add the new XML elements
     */
    private void createConfigurationElement(String enclosingTag, Map<String, String> enclosingTagAttr, Map<String, String> properties, Directives directives) {
        if (properties == null || properties.isEmpty()) {
            return;
        }

        directives.add(enclosingTag);
        for (String attrName : enclosingTagAttr.keySet()) {
            directives.attr(attrName, enclosingTagAttr.get(attrName));
        }

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            directives.add("property")
                    .add("name")
                    .set(entry.getKey())
                    .up()
                    .add("value")
                    .set(entry.getValue())
                    .up()
                    .up();
        }
        directives.up();
    }

    /**
     * Add the elements where a given key has multiple values
     * An example of this is the arg tag
     *
     * @param entry A mapping of key to a list of values
     * @param directives The Xembly Directives object to which to add the new XML elements
     */
    private void addKeyMultiValueElements(Map.Entry<String, List<String>> entry, Directives directives) {
        for (String value : entry.getValue()) {
            directives.add(entry.getKey())
                    .set(value)
                    .up();
        }
    }

    /**
     * Get the ActionType configuration from a type name
     * This caches the results to avoid searching the whole configuration repeatedly
     *
     * @param type The name of the action type for which to retrieve the configuration
     * @return An ActionType object representing the configuration for the given action type
     */
    private ActionType getActionType(String type) {
        ActionType result = actionTypeCache.get(type);
        if (result == null) {
            result = config.getActionTypeByName(type);
            actionTypeCache.put(type, result);
        }

        return result;
    }

    /**
     * Write an XML document to a file
     *
     * @param outputFileAbsolutePath The output file path for the XML file.
     * @param xmlDoc The document to write out
     * @param transformer The XML transformer used to produce the output
     * @param name The name of the workflow
     * @param currentDateString A string representation of the current date, used in a comment in the output file
     * @throws TransformerException
     * @throws IOException
     */
    private void writeDocument(String outputFileAbsolutePath, Document xmlDoc, Transformer transformer, String name, String currentDateString) throws TransformerException, IOException {
        DOMSource source = new DOMSource(xmlDoc);

        File outputFile = new File(outputFileAbsolutePath + ".xml");
        StreamResult result = new StreamResult(outputFile);
        transformer.transform(source, result);

        // We want a comment indicating that this file is autogenerated as the first line
        // There's no good way to do this from the XML DOM, so we have to do it manually.
        BufferedReader reader = new BufferedReader(new FileReader(outputFile));
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }
        reader.close();

        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
        for (int i = 0; i < lines.size(); i++) {
            line = lines.get(i);
            // Comments have to appear after the <?xml line
            if (i == 1) {
                writer.write(String.format("<!-- %s workflow autogenerated by Arbiter on %s -->\n", name, currentDateString));
            }
            writer.write(line + "\n");
        }
        writer.close();
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
    private Action getTransition(DirectedAcyclicGraph<Action, DefaultEdge> workflowGraph, Action a) {
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
     * Finds the first action in the graph of a given type
     * This is intended for use when only one action of the given type exists
     * If more than one exists the traversal order and thus the resulting action is not guaranteed
     *
     * @param workflowGraph The graph in which to find the action
     * @param type The type of action to find
     * @return The action of the given type, or null if none exists
     */
    private Action getActionByType(DirectedAcyclicGraph<Action, DefaultEdge> workflowGraph, final String type) {
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
