package net.achalaggarwal.arbiter.util;

import org.w3c.dom.Document;
import org.xembly.Directives;
import org.xembly.ImpossibleModificationException;
import org.xembly.Xembler;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class DocumentWriter {
  private DocumentWriter() { }
  public static final String XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n";

  /**
   * Write an XML document to a StreamResult
   *
   * @param directives The document to write out
   * @param streamResult The output StreamResult
   * @throws TransformerException
   * @throws ParserConfigurationException
   */
  public static void writeToStreamResult(Directives directives, StreamResult streamResult) throws ParserConfigurationException, TransformerException {
    Document xmlDoc = DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .newDocument();

    try {
      new Xembler(directives).apply(xmlDoc);
    } catch (ImpossibleModificationException e) {
      throw new RuntimeException(e);
    }

    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer transformer = transformerFactory.newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

    transformer.transform(
            new DOMSource(xmlDoc),
            streamResult
    );
  }
}
