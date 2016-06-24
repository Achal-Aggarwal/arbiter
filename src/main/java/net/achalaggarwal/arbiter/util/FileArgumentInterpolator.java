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

package net.achalaggarwal.arbiter.util;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.nio.charset.Charset.defaultCharset;

/**
 * Performs variable interpolation using the named arguments from an Action
 * Prefix for interpolation is $$
 *
 * @author Andrew Johnson
 */
public class FileArgumentInterpolator {
    private FileArgumentInterpolator() { }

    public static final String PREFIX = "@@";
    public static final String SUFFIX = "@@";

    /**
     * Performs variable interpolation using the named arguments from an Action
     * This will create a new map if any interpolation is performed
     *
     * @param baseDirPath Path of parent dir to use to find a file
     * @param input The positional arguments possibly containing keys to be interpolated
     *
     * @return A copy of input with variable interpolation performed
     */
    public static Map<String, List<String>> interpolateFileVars(final String baseDirPath, Map<String, List<String>> input) {
        if (input == null) {
            return input;
        }

        return Maps.transformValues(input, new Function<List<String>, List<String>>() {
            @Override
            public List<String> apply(List<String> input) {
                List<String> result = new ArrayList<>(input.size());
                for (String s : input) {
                    if (s.startsWith(PREFIX) && s.endsWith(SUFFIX)) {
                        try {
                            String fileContent = StringUtils.join(
                              Files.readAllLines(
                                new File(baseDirPath, s.substring(2, s.length() - 2)).toPath(),
                                defaultCharset()
                              ),
                              System.lineSeparator()
                            );
                            result.add(fileContent);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        result.add(s);
                    }
                }
                return result;
            }
        });
    }
}
