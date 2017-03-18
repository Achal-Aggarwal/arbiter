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

package net.achalaggarwal.arbiter.config;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the configuration for a type of Oozie action
 *
 * @author Andrew Johnson
 */
@Data
public class ActionType {
    private String tag;
    private String name;
    private String xmlns;
    private String cred;
    private Integer retryMax;
    private Integer retryInterval;
    private Map<String, List<String>> defaultArgs;
    private Map<String, String> properties = new HashMap<>();
    private Map<String, String> defaultInterpolations;
    private boolean lowPrecedence;
    private int configurationPosition;
    private int preparePosition;
    private Prepare prepare;

    public boolean isLowPrecedence() {
        return lowPrecedence;
    }
}
