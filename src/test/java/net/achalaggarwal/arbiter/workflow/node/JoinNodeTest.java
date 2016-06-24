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
import static org.junit.Assert.assertEquals;

public class JoinNodeTest {
  @Test(expected = RuntimeException.class)
  public void shouldThrowExceptionIfTransitionNodeIsNull() {
    new JoinNode(new Action(), null);
  }

  @Test
  public void shouldBuildJoinNodeFromJoinAction() throws TransformerException, ParserConfigurationException {
    Directives joinNode = new JoinNode(
      new Action() { {
        setName("join-0");
      } },
      new Action() { {
        setName("next-action");
      } }
    ).buildNode();

    StringWriter writer = new StringWriter();
    DocumentWriter.writeToStreamResult(joinNode, new StreamResult(writer));

    assertEquals(XML_HEAD + "<join name=\"join-0\" to=\"next-action\"/>\n", writer.toString());
  }
}