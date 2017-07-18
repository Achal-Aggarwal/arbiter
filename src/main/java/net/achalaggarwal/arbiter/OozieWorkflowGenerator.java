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

import net.achalaggarwal.arbiter.config.Config;
import net.achalaggarwal.arbiter.exception.WorkflowGraphException;
import net.achalaggarwal.arbiter.util.GraphvizGenerator;
import net.achalaggarwal.arbiter.workflow.WorkflowGraphBuilder;
import net.achalaggarwal.arbiter.workflow.WorkflowNode;
import net.achalaggarwal.arbiter.workflow.node.ActionNode;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.graph.DefaultEdge;
import org.xembly.Directives;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import static net.achalaggarwal.arbiter.util.DocumentWriter.writeToStreamResult;
import static org.apache.commons.io.FileUtils.writeStringToFile;

/**
 * Generates Oozie workflows from Arbiter workflows
 *
 * @author Andrew Johnson
 */
public class OozieWorkflowGenerator {
    private static final Logger LOG = Logger.getLogger(OozieWorkflowGenerator.class);

    private Config config;

    public OozieWorkflowGenerator(Config config) {
        this.config = config;
    }

    /**
     * Generate Oozie workflows from Arbiter workflows
     * @param workflows The workflows to convert
     * @param generateGraphviz Indicate if Graphviz graphs should be generated for workflows
     * @param graphvizFormat The format in which Graphviz graphs should be generated if enabled
     */
    public void generateOozieWorkflows(Map<File, Workflow> workflows, boolean generateGraphviz, String graphvizFormat) throws IOException, ParserConfigurationException, TransformerException, SAXException {
        for (File inputFile : workflows.keySet()) {
            String parentDir = inputFile.getParentFile().getAbsolutePath();
            String inputFileName = inputFile.getName().substring(0, inputFile.getName().lastIndexOf("."));
            Workflow workflow = workflows.get(inputFile);

            DirectedAcyclicGraph<YamlElement, DefaultEdge> workflowGraph = null;
            DirectedAcyclicGraph<YamlElement, DefaultEdge> inputGraph = null;
            try {
                inputGraph = WorkflowGraphBuilder.buildInputGraph(workflow);
                workflowGraph = WorkflowGraphBuilder.buildWorkflowGraph(inputGraph, workflow, config);
            } catch (WorkflowGraphException w) {
                LOG.error("Unable to generate workflow", w);
                System.exit(1);
            }

            Directives directives =
              new WorkflowNode(workflow, workflowGraph, config, inputFile)
              .buildDOM();
            writeDocument(parentDir, inputFileName, directives);

            if (generateGraphviz) {
                generateGraphs(graphvizFormat, parentDir, inputFileName, workflowGraph, inputGraph);
            }
        }
    }

    private void generateGraphs(String graphvizFormat, String parentDir, String inputFileName, DirectedAcyclicGraph<YamlElement, DefaultEdge> workflowGraph, DirectedAcyclicGraph<YamlElement, DefaultEdge> inputGraph) throws IOException {
        File dotFilesBaseDir = new File(parentDir, "dot");
        FileUtils.forceMkdir(dotFilesBaseDir);
        String outputDotFilesAbsolutePath = new File(dotFilesBaseDir, inputFileName).getAbsolutePath();
        GraphvizGenerator.generateGraphviz(inputGraph, outputDotFilesAbsolutePath + "-input.dot", graphvizFormat);
        GraphvizGenerator.generateGraphviz(workflowGraph, outputDotFilesAbsolutePath + ".dot", graphvizFormat);
    }

    /**
     * Write an XML document to a file
     *
     * @param parentDir The parent dir of output file for the XML file.
     * @param inputFileName The name of output file for the XML file.
     * @param directives The document to write out
     * @throws TransformerException
     * @throws IOException
     */
    private void writeDocument(String parentDir, String inputFileName, Directives directives) throws TransformerException, IOException, ParserConfigurationException, SAXException {
        File outputFile = new File(parentDir, inputFileName + ".xml");
        writeToStreamResult(directives, new StreamResult(outputFile));
        writeStringToFile(outputFile, replaceArbiterCommentTags(outputFile));
    }

    private static String replaceArbiterCommentTags(File outputFile) throws IOException {
        return FileUtils.readFileToString(outputFile)
          .replace("<" + ActionNode.ARBITER_COMMENT_TAG + ">", "<!--")
          .replace("</" + ActionNode.ARBITER_COMMENT_TAG + ">", "-->");
    }
}
