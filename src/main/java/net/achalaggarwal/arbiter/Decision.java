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

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Represents an Oozie Action
 *
 * @author Andrew Johnson
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Decision extends YamlElement {
  private List<LinkedHashMap<String, String>> cases;
  private String defaultTo;

  public Decision() {
    setType("decision");
  }
}
