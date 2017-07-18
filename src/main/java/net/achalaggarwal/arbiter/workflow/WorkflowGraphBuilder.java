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

package net.achalaggarwal.arbiter.workflow;

import com.google.common.collect.ImmutableMap;
import lombok.val;
import net.achalaggarwal.arbiter.Action;
import net.achalaggarwal.arbiter.Workflow;
import net.achalaggarwal.arbiter.YamlElement;
import net.achalaggarwal.arbiter.config.Config;
import net.achalaggarwal.arbiter.exception.WorkflowGraphException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.jgrapht.Graphs;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.achalaggarwal.arbiter.util.NamedArgumentInterpolator.interpolate;


/**
 * Takes a workflow graph specified by YAML and builds a graph in an Oozie-friendly format
 * This includes inserting start/end nodes, fork/join pairs, etc
 *
 * @author Andrew Johnson
 */
public class WorkflowGraphBuilder {
    private WorkflowGraphBuilder() {
    }

    // Every fork/join pair needs a unique name
    // To keep the names short, we just number them sequentially
    private static int forkCount = 0;

    /**
     * Build a workflow graph from the workflow definition, inserting fork/join pairs as appropriate for parallel
     *
     * @param workflow Arbiter Workflow object
     * @return DirectedAcyclicGraph DAG of the input workflow
     * @throws WorkflowGraphException
     */
    @SuppressWarnings("Duplicates")
    public static DirectedAcyclicGraph<YamlElement, DefaultEdge> buildInputGraph(Workflow workflow) throws WorkflowGraphException {
        Map<String, YamlElement> actionsByName = new LinkedHashMap<>();

        // Add all the actions to a map of string -> action
        addActionsByNames(actionsByName, workflow.getActions());
        addActionsByNames(actionsByName, workflow.getDecisions());

        // DAG of the workflow in its raw un-optimized state.
        DirectedAcyclicGraph<YamlElement, DefaultEdge> inputGraph = new DirectedAcyclicGraph<>(DefaultEdge.class);

        // Add all the actions as vertices. At this point there are no connections within the graph, just vertices.
        addGraphVertex(inputGraph, workflow.getActions());
        addGraphVertex(inputGraph, workflow.getDecisions());

        // We need to traverse a second time so all the vertices are present when adding edges
        addEdgesToGraph(inputGraph, actionsByName, workflow.getActions());
        addEdgesToGraph(inputGraph, actionsByName, workflow.getDecisions());

        return inputGraph;
    }

    private static void addEdgesToGraph(DirectedAcyclicGraph<YamlElement, DefaultEdge> inputGraph, Map<String, YamlElement> actionsByName,
                                        List<? extends YamlElement> vertexes) throws WorkflowGraphException {
        for (YamlElement a : vertexes) {
            if (a.getDependencies() != null) {
                for (String d : a.getDependencies()) {
                    YamlElement source = actionsByName.get(d);
                    if (source == null) {
                        throw new WorkflowGraphException("Missing action for dependency " + d);
                    }

                    // Add the edge between the dep and the action
                    try {
                        inputGraph.addDagEdge(source, a);
                    } catch (DirectedAcyclicGraph.CycleFoundException e) {
                        throw new WorkflowGraphException("Cycle found while building original graph", e);
                    }
                }
            }
        }
    }

    private static void addActionsByNames(Map<String, YamlElement> actionsByName, List<? extends YamlElement> vertexes) {
        for (YamlElement a : vertexes) {
            actionsByName.put(a.getName(), a);
        }
    }

    private static void addGraphVertex(DirectedAcyclicGraph<YamlElement, DefaultEdge> inputGraph, List<? extends YamlElement> vertexes) {
        for (YamlElement a : vertexes) {
            inputGraph.addVertex(a);
        }
    }

    /**
     * Build a workflow graph from the workflow definition, inserting fork/join pairs as appropriate for parallel
     *
     * @param inputGraph
     * @param workflow   Arbiter Workflow object
     * @param config     Arbiter Config object
     * @return DirectedAcyclicGraph DAG of the workflow
     * @throws WorkflowGraphException
     */
    public static DirectedAcyclicGraph<YamlElement, DefaultEdge> buildWorkflowGraph(DirectedAcyclicGraph<YamlElement, DefaultEdge> inputGraph, Workflow workflow, Config config) throws WorkflowGraphException {
        forkCount = 0;

        try {
            // Process the graph into its properly connected and organized structure.
            val workflowGraphTriple = processSubcomponents(inputGraph);

            // Final DAG we will be returning
            DirectedAcyclicGraph<YamlElement, DefaultEdge> workflowGraph = workflowGraphTriple.getLeft();
            YamlElement startTransitionNode = workflowGraphTriple.getMiddle();
            YamlElement endTransitionNode = workflowGraphTriple.getRight();

            // These are the standard control flow nodes that must be present in every workflow
            YamlElement start = Action.getStartAction();
            workflowGraph.addVertex(start);
            workflowGraph.addDagEdge(start, startTransitionNode);

            YamlElement end = Action.getEndAction();
            workflowGraph.addVertex(end);

            setCustomErrorHandlerIfPresent(workflow, workflowGraph, endTransitionNode, end);

            // The kill node will be used as the error transition when generating the XML as appropriate
            // There is no need to add any edges to it now
            if (config.getKillMessage() != null && config.getKillName() != null) {
                workflowGraph.addVertex(Action.getKillAction(
                        config.getKillName(),
                        interpolate(config.getKillMessage(), ImmutableMap.of("name", workflow.getName()), null)
                ));
            }

            return workflowGraph;
        } catch (DirectedAcyclicGraph.CycleFoundException e) {
            throw new WorkflowGraphException("Cycle found while generating workflow", e);
        }
    }

    private static void setCustomErrorHandlerIfPresent(Workflow workflow, DirectedAcyclicGraph<YamlElement, DefaultEdge> workflowGraph, YamlElement endTransitionNode, YamlElement end) throws DirectedAcyclicGraph.CycleFoundException {
        if (workflow.getErrorHandler() != null) {
            workflowGraph.addVertex(workflow.getErrorHandler());
            workflowGraph.addDagEdge(workflow.getErrorHandler(), end);
            workflowGraph.addDagEdge(endTransitionNode, workflow.getErrorHandler());
        } else {
            workflowGraph.addDagEdge(endTransitionNode, end);
        }
    }

    /**
     * Recursively insert fork/joins for connected subcomponents of a graph
     *
     * @param vertices    The set of vertices to process
     * @param parentGraph The parentGraph graph of these vertices
     * @return DirectedAcyclicGraph A new graph containing all the given vertices with appropriate fork/join pairs inserted
     * @throws WorkflowGraphException
     * @throws DirectedAcyclicGraph.CycleFoundException
     */
    private static DirectedAcyclicGraph<YamlElement, DefaultEdge> buildComponentGraph(Set<YamlElement> vertices, DirectedAcyclicGraph<YamlElement, DefaultEdge> parentGraph) throws WorkflowGraphException, DirectedAcyclicGraph.CycleFoundException {
        DirectedAcyclicGraph<YamlElement, DefaultEdge> subgraph = buildSubgraph(parentGraph, vertices);

        // Start by pulling out the vertices with no incoming edges
        // These can run in parallel in a fork-join
        Set<YamlElement> initialNodes = new LinkedHashSet<>();
        for (YamlElement vertex : subgraph.vertexSet()) {
            if (subgraph.inDegreeOf(vertex) == 0) {
                initialNodes.add(vertex);
            }
        }

        DirectedAcyclicGraph<YamlElement, DefaultEdge> result = new DirectedAcyclicGraph<>(DefaultEdge.class);

        if (initialNodes.isEmpty()) {
            // This is a very odd case, but just in case we'll fail if it happens
            throw new WorkflowGraphException("No nodes with inDegree = 0 found.  This shouldn't happen.");
        } else if (initialNodes.size() == 1) {
            // If there is only one node, we can't put it in a fork/join
            // In this case we'll add just that vertex to the resulting graph
            YamlElement vertex = initialNodes.iterator().next();
            result.addVertex(vertex);
            // Remove the processed vertex so that we have new unprocessed subcomponents
            subgraph.removeVertex(vertex);
        } else {
            // If there are multiple nodes, insert a fork/join pair to run them in parallel
            Pair<Action, Action> forkJoin = addForkJoin(result);
            Action fork = forkJoin.getLeft();
            Action join = forkJoin.getRight();
            for (YamlElement vertex : initialNodes) {
                result.addVertex(vertex);
                result.addDagEdge(fork, vertex);
                result.addDagEdge(vertex, join);
                // Remove the processed vertex so that we have new unprocessed subcomponents
                subgraph.removeVertex(vertex);
            }
        }

        // Now recursively process the graph with the processed nodes removed
        Triple<DirectedAcyclicGraph<YamlElement, DefaultEdge>, YamlElement, YamlElement> subComponentGraphTriple = processSubcomponents(subgraph);
        DirectedAcyclicGraph<YamlElement, DefaultEdge> subComponentGraph = subComponentGraphTriple.getLeft();

        // Having processed the subcomponents, we attach the "last" node of the graph created here to
        // the "first" node of the subcomponent graph
        YamlElement noIncoming = subComponentGraphTriple.getMiddle();
        YamlElement noOutgoing = null;

        for (YamlElement vertex : result.vertexSet()) {
            if (noOutgoing == null && result.outDegreeOf(vertex) == 0) {
                noOutgoing = vertex;
            }
        }

        Graphs.addGraph(result, subComponentGraph);
        if (noOutgoing != null && noIncoming != null && !noOutgoing.equals(noIncoming)) {
            result.addDagEdge(noOutgoing, noIncoming);
        }
        return result;
    }

    /**
     * Processes all connected subcomponents of a given graph
     *
     * @param parentGraph The graph for which to process subcomponents
     * @return A Triple with these elements - A new graph with fork/join pairs inserted, the "first" node in this graph, and the "last" node in this graph
     * @throws WorkflowGraphException
     * @throws DirectedAcyclicGraph.CycleFoundException
     */
    private static Triple<DirectedAcyclicGraph<YamlElement, DefaultEdge>, YamlElement, YamlElement> processSubcomponents(DirectedAcyclicGraph<YamlElement, DefaultEdge> parentGraph) throws WorkflowGraphException, DirectedAcyclicGraph.CycleFoundException {
        ConnectivityInspector<YamlElement, DefaultEdge> inspector = new ConnectivityInspector<>(parentGraph);
        List<Set<YamlElement>> connectedComponents = inspector.connectedSets();

        // Recursively process each connected subcomponent of the graph
        List<DirectedAcyclicGraph<YamlElement, DefaultEdge>> componentGraphs = new ArrayList<>(connectedComponents.size());
        for (Set<YamlElement> subComponent : connectedComponents) {
            componentGraphs.add(buildComponentGraph(subComponent, parentGraph));
        }

        DirectedAcyclicGraph<YamlElement, DefaultEdge> result = new DirectedAcyclicGraph<>(DefaultEdge.class);
        for (DirectedAcyclicGraph<YamlElement, DefaultEdge> subSubgraph : componentGraphs) {
            Graphs.addGraph(result, subSubgraph);
        }

        // If we have more than one subcomponent, we must insert a fork/join to run them in parallel
        if (componentGraphs.size() > 1) {
            Pair<Action, Action> forkJoin = addForkJoin(result);
            Action fork = forkJoin.getLeft();
            Action join = forkJoin.getRight();
            for (DirectedAcyclicGraph<YamlElement, DefaultEdge> subSubgraph : componentGraphs) {
                for (YamlElement vertex : subSubgraph.vertexSet()) {
                    // Vertices with no incoming edges attach directly to the fork
                    if (subSubgraph.inDegreeOf(vertex) == 0) {
                        result.addDagEdge(fork, vertex);
                    }
                    // Vertices with no outgoing edges attach directly to the join
                    if (subSubgraph.outDegreeOf(vertex) == 0) {
                        result.addDagEdge(vertex, join);
                    }
                }
            }
        }

        // The graph will now have one node with no outgoing edges and one node with no incoming edges
        // The node with no outgoing edges is the "last" node in the resulting graph
        // The node with no incoming edges is the "first" node in the resulting graph
        // These are pulled out specifically to make it easier to attach the resulting graph into another one
        YamlElement noOutgoing = null;
        YamlElement noIncoming = null;

        for (YamlElement vertex : result.vertexSet()) {
            if (noIncoming == null && result.inDegreeOf(vertex) == 0) {
                noIncoming = vertex;
            }
        }

        for (YamlElement vertex : result.vertexSet()) {
            if (noOutgoing == null && result.outDegreeOf(vertex) == 0) {
                noOutgoing = vertex;
            }
        }

        return Triple.of(result, noIncoming, noOutgoing);
    }

    /**
     * Build a subgraph of a parentGraph graph given a set of vertices
     * This is a new object and not a view on the parentGraph graph
     *
     * @param parentGraph The parentGraph component for the new subgraph to start at
     * @param vertices    The set of actions (vertices) making up the subgraph
     * @return DirectedAcyclicGraph A new graph containing only the given vertices
     */
    private static DirectedAcyclicGraph<YamlElement, DefaultEdge> buildSubgraph(DirectedAcyclicGraph<YamlElement, DefaultEdge> parentGraph, Set<YamlElement> vertices) throws DirectedAcyclicGraph.CycleFoundException {
        // Create a new DAG to serve as the subgraph
        DirectedAcyclicGraph<YamlElement, DefaultEdge> subgraph = new DirectedAcyclicGraph<>(DefaultEdge.class);

        // Add all the vertices for this subgraph
        for (YamlElement vertex : vertices) {
            subgraph.addVertex(vertex);
        }

        // All vertices must exist in the graph before any edges can be added
        for (YamlElement vertex : vertices) {
            // Grab the parentGraph's edges between the parentGraph and this vertex and then add an edge in the subgraph to
            // match what the parentGraph has.
            for (DefaultEdge edge : parentGraph.edgesOf(vertex)) {
                subgraph.addDagEdge(parentGraph.getEdgeSource(edge), parentGraph.getEdgeTarget(edge), edge);
            }
        }

        return subgraph;
    }

    /**
     * Create a fork/join pair and add it to a graph
     *
     * @param parentGraph The graph to which to add the fork/join actions
     * @return A Pair of actions. The left action is the fork and the right action is the join
     */
    private static Pair<Action, Action> addForkJoin(DirectedAcyclicGraph<YamlElement, DefaultEdge> parentGraph) {
        Action fork = new Action();
        fork.setName("fork-" + forkCount);
        fork.setType("fork");

        Action join = new Action();
        join.setName("join-" + forkCount);
        join.setType("join");
        forkCount++;
        parentGraph.addVertex(fork);
        parentGraph.addVertex(join);

        return Pair.of(fork, join);
    }
}
