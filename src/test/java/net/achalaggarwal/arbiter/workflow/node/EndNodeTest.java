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

public class EndNodeTest {
  @Test
  public void shouldBuildEndNodeUsingEndAction() throws TransformerException, ParserConfigurationException {
    Directives endNode = new EndNode(
      new Action() { {
        setName("end-action");
      } }
    ).buildNode();

    StringWriter writer = new StringWriter();
    DocumentWriter.writeToStreamResult(endNode, new StreamResult(writer));

    assertEquals(XML_HEAD + "<end name=\"end-action\"/>\n", writer.toString());
  }
}