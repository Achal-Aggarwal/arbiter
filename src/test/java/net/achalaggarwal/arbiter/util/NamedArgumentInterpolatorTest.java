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
 */

package net.achalaggarwal.arbiter.util;

import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class NamedArgumentInterpolatorTest {
    private Map<String, String> namedArgs;
    private Map<String, String> defaultArgs;
    private Map<String, List<String>> listArgs;

    @Before
    public void setup() {
        namedArgs = new HashMap<>();
        namedArgs.put("key", "value");

        defaultArgs = new HashMap<>();
        defaultArgs.put("default", "default_value");

        listArgs = new HashMap<>();
        List<String> listValue = new ArrayList<>();
        listValue.add("list_value_one");
        listValue.add("list_value_two");
        listArgs.put("list_key", listValue);
    }

    @Test
    public void testNullNamedArgs() {
        Map<String, List<String>> args = new HashMap<>();
        args.put("one", Arrays.asList("two", "three"));

        Map<String, List<String>> result = NamedArgumentInterpolator.interpolate(args, null, null, listArgs);
        assertTrue(result == args);
    }

    @Test
    public void testReferenceEquality() {
        Map<String, List<String>> args = new HashMap<>();
        args.put("one", Arrays.asList("two", "three"));

        Map<String, List<String>> result = NamedArgumentInterpolator.interpolate(args, namedArgs, null, listArgs);

        assertFalse(args == result);
        assertEquals(args, result);
        assertFalse(args.get("one") == result.get("one"));
    }

    @Test
    public void testInterpolation() {
        Map<String, List<String>> args = new HashMap<>();
        args.put("one", Arrays.asList("$$key$$", "three"));

        Map<String, List<String>> result = NamedArgumentInterpolator.interpolate(args, namedArgs, null, listArgs);
        Map<String, List<String>> expected = new HashMap<>();
        expected.put("one", Arrays.asList("value", "three"));

        assertEquals(expected, result);
    }

    @Test
    public void testSingleStringInterpolation() {
        String input = "hello $$key$$";
        String expected = "hello value";

        assertEquals(expected, NamedArgumentInterpolator.interpolate(input, namedArgs, null));

    }

    @Test
    public void testDefaultArgs() {
        Map<String, List<String>> args = new HashMap<>();
        args.put("one", Arrays.asList("$$key$$", "three"));
        args.put("two", Arrays.asList("$$default$$", "four"));

        Map<String, List<String>> result = NamedArgumentInterpolator.interpolate(args, namedArgs, defaultArgs, listArgs);
        Map<String, List<String>> expected = new HashMap<>();
        expected.put("one", Arrays.asList("value", "three"));
        expected.put("two", Arrays.asList("default_value", "four"));

        assertEquals(expected, result);
    }

    @Test
    public void testDefaultArgsOverwrite() {
        Map<String, List<String>> args = new HashMap<>();
        args.put("one", Arrays.asList("$$key$$", "three"));
        defaultArgs.put("key", "default_value");

        Map<String, List<String>> result = NamedArgumentInterpolator.interpolate(args, namedArgs, defaultArgs, listArgs);
        Map<String, List<String>> expected = new HashMap<>();
        expected.put("one", Arrays.asList("value", "three"));

        assertEquals(expected, result);
    }

    @Test
    public void testSingleStringInterpolationWithDefaultArgs() {
        String input = "hello $$default$$";
        String expected = "hello default_value";

        assertEquals(expected, NamedArgumentInterpolator.interpolate(input, namedArgs, defaultArgs));
    }

    @Test
    public void testSingleStringInterpolationWithDefaultArgsOverwrite() {
        defaultArgs.put("key", "default_value");
        String input = "hello $$key$$";
        String expected = "hello value";

        assertEquals(expected, NamedArgumentInterpolator.interpolate(input, namedArgs, defaultArgs));
    }

    @Test
    public void testListInterpolation() {
        Map<String, List<String>> args = new HashMap<>();
        args.put("one", Arrays.asList("$$key$$", "three", "$$list_key$$"));

        Map<String, List<String>> result = NamedArgumentInterpolator.interpolate(args, namedArgs, null, listArgs);
        Map<String, List<String>> expected = new HashMap<>();
        expected.put("one", Arrays.asList("value", "three", "list_value_one", "list_value_two"));

        assertEquals(expected, result);
        assertFalse(listArgs.containsKey("list_key"));
    }

    @Test
    public void testListInterpolationWithoutStandaloneKey() {
        Map<String, List<String>> args = new HashMap<>();
        args.put("one", Arrays.asList("$$key$$", "three", "other_$$list_key$$_stuff"));

        Map<String, List<String>> result = NamedArgumentInterpolator.interpolate(args, namedArgs, null, listArgs);
        Map<String, List<String>> expected = new HashMap<>();
        expected.put("one", Arrays.asList("value", "three", "other_$$list_key$$_stuff"));

        assertEquals(expected, result);
        assertTrue(listArgs.containsKey("list_key"));
    }
}