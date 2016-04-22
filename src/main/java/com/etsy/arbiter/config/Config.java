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

package com.etsy.arbiter.config;

import com.etsy.arbiter.Credential;
import com.etsy.arbiter.Global;
import lombok.Data;
import lombok.Getter;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.constructor.Constructor;
import sun.org.mozilla.javascript.internal.annotations.JSGetter;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a complete Arbiter configuration
 *
 * @author Andrew Johnson
 */
@Data
public class Config {
    /**
     * Defines how to construct a Config objects when reading from YAML
     *
     * @return A snakeyaml Constructor instance that will be used to create Config objects
     */
    public static Constructor getYamlConstructor() {
        Constructor configConstructor = new Constructor(Config.class);
        TypeDescription configDescription = new TypeDescription(Config.class);
        configDescription.putListPropertyType("credentials", Credential.class);
        configDescription.putListPropertyType("actionTypes", ActionType.class);
        configConstructor.addTypeDescription(configDescription);

        return configConstructor;
    }

    private List<ActionType> actionTypes;
    private String killName;
    private String killMessage;
    private List<Credential> credentials = new ArrayList<>();

    /**
     * Sets the precedence for this Config
     *
     * @param precedence true if this is low precedence, false otherwise
     */
    public void setLowPrecedence(boolean precedence) {
        for (ActionType a : actionTypes) {
            a.setLowPrecedence(precedence);
        }
    }

    /**
     * Gets an ActionType from this configuration by name
     *
     * @param name The name of the ActionType to find
     * @return The ActionType corresponding to the given name, or null if none is found
     */
    public ActionType getActionTypeByName(String name) {
        for (ActionType a : actionTypes) {
            if (a.getName().equals(name)) {
                return a;
            }
        }

        return null;
    }
}
