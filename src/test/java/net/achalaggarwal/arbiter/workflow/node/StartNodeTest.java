package net.achalaggarwal.arbiter.workflow.node;

import net.achalaggarwal.arbiter.Action;
import net.achalaggarwal.arbiter.util.DocumentWriter;
import org.junit.Test;
import org.xembly.Directives;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

import static net.achalaggarwal.arbiter.util.DocumentWriter.XML_HEAD;
import static org.junit.Assert.*;

public class StartNodeTest {
  @Test
  public void shouldBuildStartNodeUsingNextTransitionAction() throws TransformerException, ParserConfigurationException {
    Directives startNode = new StartNode(
      new Action() { {
        setName("first-action");
      } }
    ).buildNode();

    StringWriter writer = new StringWriter();
    DocumentWriter.writeToStreamResult(startNode, new StreamResult(writer));

    assertEquals(XML_HEAD + "<start to=\"first-action\"/>\n", writer.toString());
  }
}