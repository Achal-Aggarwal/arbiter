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

package com.etsy.arbiter;

import com.etsy.arbiter.config.Config;
import com.etsy.arbiter.config.ConfigurationMerger;
import com.etsy.arbiter.exception.ConfigurationException;
import com.etsy.arbiter.util.YamlReader;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.cli.*;
import org.apache.commons.io.filefilter.TrueFileFilter;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.apache.commons.io.FileUtils.listFiles;
import static org.apache.commons.io.filefilter.FileFilterUtils.suffixFileFilter;

/**
 * Entry point for Arbiter
 *
 * @author Andrew Johnson
 */
public class Arbiter {
    private Arbiter() { }

    public static void main(String[] args) throws ParseException, ConfigurationException, IOException, ParserConfigurationException, TransformerException {
        Options options = getOptions();

        CommandLineParser cmd = new GnuParser();
        CommandLine parsed = cmd.parse(options, args);

        if (parsed.hasOption("h")) {
            printUsage(options);
        }

        if (!parsed.hasOption("i")) {
            throw new ParseException("Missing required argument: i");
        }

        String configFile = parsed.getOptionValue("c");
        String lowPrecedenceConfigFile = parsed.getOptionValue("l");

        List<Config> parsedConfigFiles = readConfigFiles(configFile, false);
        parsedConfigFiles.addAll(readConfigFiles(lowPrecedenceConfigFile, true));
        Config merged = ConfigurationMerger.mergeConfiguration(parsedConfigFiles);

        String inputFile = parsed.getOptionValue("i");
        Map<File, Workflow> workflows = readWorkflowFiles(inputFile);

        boolean generateGraphviz = parsed.hasOption("g");
        String graphvizFormat = parsed.getOptionValue("g", "svg");

        OozieWorkflowGenerator generator = new OozieWorkflowGenerator(merged);
        generator.generateOozieWorkflows(workflows, generateGraphviz, graphvizFormat);
    }

    /**
     * Reads in a list of workflow files
     *
     * @param file The file/dir to read
     * @return A list of Workflow objects corresponding to the given files
     */
    public static Map<File, Workflow> readWorkflowFiles(String file) {
        if (file == null) {
            return Maps.newHashMap();
        }

        YamlReader<Workflow> reader = new YamlReader<>(Workflow.getYamlConstructor());

        HashMap<File, Workflow> result = Maps.newHashMap();

        File f = new File(file);
        if (f.isDirectory()) {
            for (File yamlFile : getOnlyYamlFiles(f)) {
                result.put(yamlFile, reader.read(yamlFile));
            }
        } else {
            result.put(f, reader.read(f));
        }

        return result;
    }

    public static Collection<File> getOnlyYamlFiles(File file) {
        return listFiles(file, suffixFileFilter(".yaml"), TrueFileFilter.INSTANCE);
    }

    /**
     * Reads in a list of configuration files
     *
     * @param file The file/dir to read
     * @param lowPrecedence Whether or not these configurations should be marked as low-priority
     * @return A List of Config objects corresponding to the given files
     */
    public static List<Config> readConfigFiles(String file, boolean lowPrecedence) {
        if (file == null) {
            return Lists.newArrayList();
        }

        YamlReader<Config> reader = new YamlReader<>(Config.getYamlConstructor());
        
        ArrayList<Config> result = Lists.newArrayList();
        
        File f = new File(file);
        if (f.isDirectory()) {
            for (File yamlFile : getOnlyYamlFiles(f)) {
                Config c = reader.read(yamlFile);
                c.setLowPrecedence(lowPrecedence);
                result.add(c);
            }
        } else {
            Config c = reader.read(f);
            c.setLowPrecedence(lowPrecedence);
            result.add(c);
        }

        return result;
    }

    /**
     * Prints the usage for Arbiter
     *
     * @param options The CLI options
     */
    private static void printUsage(Options options) {
        HelpFormatter help = new HelpFormatter();
        help.printHelp("arbiter", options);
        System.exit(0);
    }

    /**
     * Construct the CLI Options
     *
     * @return The Options object representing the supported CLI options
     */
    @SuppressWarnings("static-access")
    private static Options getOptions() {
        Option config = OptionBuilder
                .withArgName("config")
                .withLongOpt("config")
                .hasArgs()
                .withDescription("Configuration file/dir")
                .create("c");

        Option lowPrecedenceConfig = OptionBuilder
                .withArgName("lowPrecedenceConfig")
                .withLongOpt("low-priority-config")
                .hasArgs()
                .withDescription("Low-priority configuration file")
                .create("l");

        Option inputFile = OptionBuilder
                .withArgName("input")
                .withLongOpt("input")
                .hasArgs()
                .withDescription("Input Arbiter workflow file/dir")
                .create("i");

        Option help = OptionBuilder
                .withArgName("help")
                .withLongOpt("help")
                .withDescription("Print usage")
                .create("h");

        Option graphviz = OptionBuilder
                .withArgName("graphviz")
                .withLongOpt("graphviz")
                .hasOptionalArg()
                .withDescription("Generate the Graphviz DOT file and PNG")
                .create("g");

        Options options = new Options();
        options.addOption(config)
                .addOption(lowPrecedenceConfig)
                .addOption(inputFile)
                .addOption(help)
                .addOption(graphviz);

        return options;
    }
}
