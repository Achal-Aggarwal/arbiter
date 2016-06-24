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

public class KillNodeTest {
  @Test
  public void shouldBuildKillNodeFromKillAction() throws TransformerException, ParserConfigurationException {
    Directives killNode = new KillNode(
      new Action() { {
        setName("kill-0");
        setProperty("message", "kill-message");
      } }
    ).buildNode();

    StringWriter writer = new StringWriter();
    DocumentWriter.writeToStreamResult(killNode, new StreamResult(writer));

    assertEquals(XML_HEAD + "<kill name=\"kill-0\">\n" +
      "  <message>kill-message</message>\n" +
      "</kill>\n", writer.toString());
  }
}