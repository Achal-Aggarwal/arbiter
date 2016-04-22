package com.etsy.arbiter.config;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Global {
  private Map<String, List<String>> defaultArgs;
  private Map<String, String> properties;
  private int configurationPosition = 3;
}
