package net.achalaggarwal.arbiter.workflow.node;

import net.achalaggarwal.arbiter.config.Credential;
import net.achalaggarwal.arbiter.workflow.Node;
import org.xembly.Directives;

import java.util.ArrayList;
import java.util.HashMap;

public class CredentialNode extends Node {
  private ArrayList<Credential> credentials;

  public CredentialNode(ArrayList<Credential> credentials) {
    this.credentials = credentials;
  }

  @Override
  public Directives buildNode() {
    Directives directives = new Directives();

    directives.add("credentials");

    for (final Credential credential : credentials) {
      HashMap<String, String> attributes = new HashMap<String, String>() { {
        put("name", credential.getName());
        put("type", credential.getType());
      } };

      new ConfigurationNode("credential", attributes, credential.getProperties())
        .appendTo(directives);
    }

    return directives.up();
  }
}
