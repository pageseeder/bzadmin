package org.pageseeder.bzadmin.psml;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jspecify.annotations.Nullable;

/**
 * Reads the metadata of a PSML document with the JDK's StAX parser and the bounds of
 * {@link PsmlDocument}.
 *
 * <p>The JDK's parser rather than the one on the classpath, so that the security properties are
 * known to apply: DTDs are not supported and external entities are neither resolved nor loaded.
 */
final class PsmlMetadataReader {

  private static final Set<String> FRAGMENTS = Set.of("fragment", "media-fragment", "properties-fragment", "xref-fragment");

  private static final String ELLIPSIS = "…";

  private PsmlMetadataReader() {
  }

  /**
   * Parses a document, setting what it finds and the status of the parse on the metadata.
   *
   * @param in       The content of the document.
   * @param metadata The metadata to complete.
   * @throws IOException if the content cannot be read.
   */
  static void read(InputStream in, PsmlDocument metadata) throws IOException {
    BoundedInputStream bounded = new BoundedInputStream(in, PsmlDocument.MAX_BYTES);
    XMLStreamReader reader = null;
    try {
      reader = factory().createXMLStreamReader(bounded);
      metadata.status = parse(reader, metadata);
    } catch (XMLStreamException ex) {
      if (bounded.exceeded) {
        metadata.status = PsmlDocument.Status.TOO_LARGE;
      } else if (ex.getNestedException() instanceof IOException) {
        throw (IOException) ex.getNestedException();
      } else {
        malformed(ex, metadata);
      }
    } finally {
      if (reader != null) close(reader);
    }
  }

  private static PsmlDocument.Status parse(XMLStreamReader reader, PsmlDocument metadata) throws XMLStreamException {
    Parser parser = new Parser(reader, metadata);
    while (reader.hasNext()) {
      PsmlDocument.Status status = parser.next(reader.next());
      if (status != null) return status;
    }
    return PsmlDocument.Status.OK;
  }

  private static String cut(String value) {
    return value.length() > PsmlDocument.MAX_VALUE_LENGTH ? value.substring(0, PsmlDocument.MAX_VALUE_LENGTH) + ELLIPSIS : value;
  }

  private static void malformed(XMLStreamException ex, PsmlDocument metadata) {
    metadata.status = PsmlDocument.Status.MALFORMED;
    Location location = ex.getLocation();
    if (location != null) {
      metadata.line = Math.max(location.getLineNumber(), 0);
      metadata.column = Math.max(location.getColumnNumber(), 0);
    }
    // The JDK's messages start with the location: "ParseError at [row,col]:[1,7]\nMessage: ..."
    String message = ex.getMessage();
    if (message != null) {
      int start = message.indexOf("Message: ");
      metadata.message = cut(start >= 0 ? message.substring(start + "Message: ".length()) : message);
    }
  }

  private static XMLInputFactory factory() {
    XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLInputFactory.IS_COALESCING, false);
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setXMLResolver((publicId, systemId, base, namespace) -> {
      throw new XMLStreamException("External resources are not loaded");
    });
    return factory;
  }

  private static void close(XMLStreamReader reader) {
    try {
      reader.close();
    } catch (XMLStreamException ex) {
      // The stream is closed by the caller
    }
  }

  /**
   * Sets the metadata from the events of a document, within the bounds of {@link PsmlDocument}.
   */
  private static final class Parser {

    private final XMLStreamReader reader;

    private final PsmlDocument metadata;

    private final Deque<String> ancestors = new ArrayDeque<>();

    private int elements = 0;

    /** The text of the display title while it is read. */
    private @Nullable StringBuilder displaytitle;

    Parser(XMLStreamReader reader, PsmlDocument metadata) {
      this.reader = reader;
      this.metadata = metadata;
    }

    /**
     * @param event The current event.
     * @return the status that ends the parse, or {@code null} to continue.
     */
    PsmlDocument.@Nullable Status next(int event) {
      if (event == XMLStreamConstants.DTD) return PsmlDocument.Status.DOCTYPE;
      if (event == XMLStreamConstants.START_ELEMENT) return start();
      if (event == XMLStreamConstants.END_ELEMENT) {
        end();
      } else if (isText(event)) {
        text();
      }
      return null;
    }

    private PsmlDocument.@Nullable Status start() {
      if (++this.elements > PsmlDocument.MAX_ELEMENTS) return PsmlDocument.Status.TOO_MANY_ELEMENTS;
      if (this.ancestors.size() == PsmlDocument.MAX_XML_DEPTH) return PsmlDocument.Status.TOO_DEEP;
      String name = this.reader.getLocalName();
      element(name, this.ancestors.peek(), this.ancestors.size());
      if ("section".equals(name)) this.metadata.sections++;
      if (FRAGMENTS.contains(name)) this.metadata.fragments++;
      this.ancestors.push(name);
      return null;
    }

    private void element(String name, @Nullable String parent, int depth) {
      if (depth == 0) {
        this.metadata.element = name;
        if ("document".equals(name)) {
          this.metadata.type = attribute(this.reader, "type");
          this.metadata.level = attribute(this.reader, "level");
        }
      } else if (depth == 2 && "uri".equals(name) && "documentinfo".equals(parent)) {
        this.metadata.docid = attribute(this.reader, "docid");
        this.metadata.title = attribute(this.reader, "title");
      } else if (depth == 3 && "displaytitle".equals(name) && "uri".equals(parent)) {
        this.displaytitle = new StringBuilder();
      }
    }

    private void end() {
      this.ancestors.pop();
      if (this.displaytitle != null && this.ancestors.size() == 3) {
        // The display title replaces the title of the URI
        String text = this.displaytitle.toString().trim();
        if (!text.isEmpty()) this.metadata.title = cut(text);
        this.displaytitle = null;
      }
    }

    private void text() {
      StringBuilder text = this.displaytitle;
      if (text != null && text.length() <= PsmlDocument.MAX_VALUE_LENGTH) {
        text.append(this.reader.getTextCharacters(), this.reader.getTextStart(), Math.min(this.reader.getTextLength(), PsmlDocument.MAX_VALUE_LENGTH + 1));
      }
    }

    private static boolean isText(int event) {
      return event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA || event == XMLStreamConstants.SPACE;
    }

    private static @Nullable String attribute(XMLStreamReader reader, String name) {
      String value = reader.getAttributeValue(null, name);
      return value != null ? cut(value) : null;
    }

  }

  /**
   * Fails a read past a number of bytes, in case the file grew since its size was checked.
   */
  private static final class BoundedInputStream extends FilterInputStream {

    private long remaining;

    private boolean exceeded;

    BoundedInputStream(InputStream in, long max) {
      super(in);
      this.remaining = max;
    }

    @Override
    public int read() throws IOException {
      int b = super.read();
      if (b != -1) count(1);
      return b;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int n = super.read(buffer, offset, length);
      if (n > 0) count(n);
      return n;
    }

    @Override
    public long skip(long n) throws IOException {
      long skipped = super.skip(n);
      count(skipped);
      return skipped;
    }

    private void count(long n) throws IOException {
      this.remaining -= n;
      if (this.remaining < 0) {
        this.exceeded = true;
        throw new IOException("More than " + PsmlDocument.MAX_BYTES + " bytes");
      }
    }
  }

}
