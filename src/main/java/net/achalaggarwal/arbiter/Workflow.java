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

import net.achalaggarwal.arbiter.config.Credential;
import net.achalaggarwal.arbiter.config.Global;
import lombok.Data;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.constructor.Constructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Arbiter workflow
 *
 * @author Andrew Johnson
 */
@Data
public class Workflow {
    /**
     * Defines how to construct a Workflow objects when reading from YAML
     *
     * @return A snakeyaml Constructor instance that will be used to create Workflow objects
     */
    public static Constructor getYamlConstructor() {
        Constructor workflowConstructor = new Constructor(Workflow.class);
        TypeDescription workflowDescription = new TypeDescription(Workflow.class);
        workflowDescription.putListPropertyType("global", Global.class);
        workflowDescription.putListPropertyType("actions", Action.class);
        workflowDescription.putListPropertyType("credentials", Credential.class);
        workflowConstructor.addTypeDescription(workflowDescription);

        return workflowConstructor;
    }

    private String name;
    private String xmlns;
    private List<Action> actions;
    private List<Credential> credentials = new ArrayList<>();
    private Action errorHandler;
    private Global global;
}
