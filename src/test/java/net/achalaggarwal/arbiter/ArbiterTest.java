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

import net.achalaggarwal.arbiter.config.ActionType;
import net.achalaggarwal.arbiter.config.Config;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import static org.junit.Assert.assertEquals;

public class ArbiterTest {
    @Test
    public void testReadConfigFiles() throws IOException {
        File tempFile = writeToTempFile("testconfig.yaml");

        List<Config> result = Arbiter.readConfigFiles(tempFile.getAbsolutePath(), false);
        assertEquals(1, result.size());

        Config expected = new Config();
        expected.setKillName("kill");
        expected.setKillMessage("message");
        ActionType expectedTestAction = new ActionType();
        expected.setActionTypes(Collections.singletonList(expectedTestAction));

        expectedTestAction.setTag("testaction");
        expectedTestAction.setName("test");
        expectedTestAction.setXmlns("uri:oozie:test-action:0.1");
        Map<String, List<String>> defaultArgs = new HashMap<>();
        defaultArgs.put("a", Lists.newArrayList("a", "b", "c"));
        expectedTestAction.setDefaultArgs(defaultArgs);
        Map<String, String> properties = new HashMap<>();
        properties.put("p1", "v1");
        properties.put("p2", "v2");
        expectedTestAction.setConfigurationPosition(1);
        expectedTestAction.setProperties(properties);

        assertEquals(expected, result.get(0));
    }

    @Test
    public void testReadLowPrecedenceConfigFiles() throws IOException {
        File tempFile = writeToTempFile("testconfig.yaml");

        List<Config> result = Arbiter.readConfigFiles(tempFile.getAbsolutePath(), true);
        assertEquals(1, result.size());

        Config expected = new Config();
        expected.setKillName("kill");
        expected.setKillMessage("message");
        ActionType expectedTestAction = new ActionType();
        expected.setActionTypes(Collections.singletonList(expectedTestAction));

        expectedTestAction.setTag("testaction");
        expectedTestAction.setName("test");
        expectedTestAction.setXmlns("uri:oozie:test-action:0.1");
        Map<String, List<String>> defaultArgs = new HashMap<>();
        defaultArgs.put("a", Lists.newArrayList("a", "b", "c"));
        expectedTestAction.setDefaultArgs(defaultArgs);
        Map<String, String> properties = new HashMap<>();
        properties.put("p1", "v1");
        properties.put("p2", "v2");
        expectedTestAction.setProperties(properties);
        expectedTestAction.setConfigurationPosition(1);
        expected.setLowPrecedence(true);

        assertEquals(expected, result.get(0));
    }

    @Test
    public void testReadWorkflowFiles() throws IOException {
        File tempFile = writeToTempFile("testworkflow.yaml");

        Map<File, Workflow> result = Arbiter.readWorkflowFiles(tempFile.getAbsolutePath());
        assertEquals(1, result.size());

        Workflow expected = new Workflow();
        expected.setName("name");
        Action action1 = new Action();
        Action action2 = new Action();
        expected.setActions(Arrays.asList(action1, action2));

        action1.setName("action1");
        action1.setType("test");
        Map<String, List<String>> args = new HashMap<>();
        args.put("one", Arrays.asList("two", "three"));
        action1.setPositionalArgs(args);

        action2.setName("action2");
        action2.setType("test");
        action2.setDependencies(Sets.newHashSet("action1"));
        args = new HashMap<>();
        args.put("two", Arrays.asList("four", "six"));
        action2.setPositionalArgs(args);

        Action error = new Action();
        error.setName("error");
        error.setType("errorTest");
        args = new HashMap<>();
        args.put("e", Arrays.asList("f", "g"));
        Map<String, String> namedArgs = new HashMap<>();
        namedArgs.put("nameArg", "value");
        action2.setNamedArgs(namedArgs);
        error.setPositionalArgs(args);
        expected.setErrorHandler(error);

        assertEquals(expected, result.get(result.keySet().iterator().next()));

    }

    private File writeToTempFile(String resourceName) throws IOException {
        URL resource = getClass().getClassLoader().getResource(resourceName);
        if (resource == null) {
            throw new RuntimeException("Unable to load resource " + resourceName);
        }

        File tempFile = new File(FileUtils.getTempDirectory(), System.nanoTime() + "");
        FileUtils.copyURLToFile(resource, tempFile);

        return tempFile;
    }
}