package org.pageseeder.bzadmin.redirect;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.GlobalSettings;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parses a mapping file as Berlioz's {@code ConfigLoader} accepts it, without calling it, since it
 * logs on every load: a Berlioz DOCTYPE removed in the same way, no other DOCTYPE, no external
 * entity, and the schema of the Berlioz JAR.
 *
 * <p>The schema is not a public Berlioz API: when it cannot be found or compiled, the file is only
 * checked for well-formedness, and the result says it was not validated.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
final class MappingParser {

  /** The most rules listed; the others are counted. */
  static final int MAX_RULES = 2000;

  /** The most elements parsed. */
  static final int MAX_ELEMENTS = 100_000;

  /** The most characters of a parser message. */
  static final int MAX_MESSAGE = 512;

  /** The schemas of the Berlioz JAR, empty when unavailable, loaded once per type. */
  private static final Map<FilterType, Optional<Schema>> SCHEMAS = new ConcurrentHashMap<>();

  private MappingParser() {
  }

  /** A rule as written in the file. */
  static final class Rule {

    final @Nullable String from;

    final @Nullable String to;

    final @Nullable String permanent;

    Rule(@Nullable String from, @Nullable String to, @Nullable String permanent) {
      this.from = from;
      this.to = to;
      this.permanent = permanent;
    }

  }

  /** The result of parsing a file. */
  static final class Result {

    /** Whether the file was parsed to the end: well-formed, without a DOCTYPE other than Berlioz's. */
    final boolean parsed;

    /** Whether the file has more elements than parsed. */
    final boolean tooLarge;

    /** Whether the file is valid: parsed, and valid against the schema when it is available. */
    final boolean valid;

    /** Why the file is not valid. */
    final @Nullable String message;

    /** The number of rules in the file. */
    final int total;

    /** The rules listed: at most {@link #MAX_RULES}. */
    final List<Rule> rules;

    Result(boolean parsed, boolean tooLarge, boolean valid, @Nullable String message, int total, List<Rule> rules) {
      this.parsed = parsed;
      this.tooLarge = tooLarge;
      this.valid = valid;
      this.message = message;
      this.total = total;
      this.rules = rules;
    }

  }

  /**
   * @param type The type of filter.
   * @return the schema of the Berlioz JAR for its files, or {@code null} if it cannot be loaded.
   */
  static @Nullable Schema schema(FilterType type) {
    return SCHEMAS.computeIfAbsent(type, t -> Optional.ofNullable(loadSchema(t))).orElse(null);
  }

  private static @Nullable Schema loadSchema(FilterType type) {
    try (InputStream in = GlobalSettings.class.getResourceAsStream(type.schemaResource())) {
      if (in == null) return null;
      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      return factory.newSchema(new StreamSource(in));
    } catch (IOException | SAXException | RuntimeException ex) {
      return null;
    }
  }

  /**
   * @param bytes  The content of the file.
   * @param type   The type of filter.
   * @param schema The schema to validate against, {@code null} to check well-formedness only.
   * @return the result.
   */
  static Result parse(byte[] bytes, FilterType type, @Nullable Schema schema) {
    byte[] clean = removeBerliozDoctype(bytes);
    Handler handler = new Handler(type);
    try {
      newParser().parse(new InputSource(new ByteArrayInputStream(clean)), handler);
    } catch (TooManyElements ex) {
      return new Result(false, true, false, null, handler.total, List.of());
    } catch (SAXException ex) {
      return new Result(false, false, false, message(ex), handler.total, List.of());
    } catch (IOException | ParserConfigurationException ex) {
      return new Result(false, false, false, ex.getClass().getSimpleName(), handler.total, List.of());
    }
    String invalid = schema != null ? validate(clean, schema) : null;
    return new Result(true, false, invalid == null, invalid, handler.total, handler.rules);
  }

  private static @Nullable String validate(byte[] bytes, Schema schema) {
    Validator validator = schema.newValidator();
    // The parse already rejected any DOCTYPE; these only restrict a validator that supports them
    for (String property : new String[]{XMLConstants.ACCESS_EXTERNAL_DTD, XMLConstants.ACCESS_EXTERNAL_SCHEMA}) {
      try {
        validator.setProperty(property, "");
      } catch (SAXException ex) {
        // Not supported by this validator
      }
    }
    try {
      validator.validate(new StreamSource(new ByteArrayInputStream(bytes)));
      return null;
    } catch (SAXException ex) {
      return message(ex);
    } catch (IOException ex) {
      return ex.getClass().getSimpleName();
    }
  }

  /**
   * Removes a DOCTYPE that contains {@code -//Berlioz} or {@code //Weborganic}, from
   * {@code <!DOCTYPE } to the first {@code >} after it, as Berlioz's {@code ConfigLoader} does.
   *
   * @param bytes The content of the file.
   * @return the content without it, or the same content.
   */
  static byte[] removeBerliozDoctype(byte[] bytes) {
    int start = find(bytes, "<!DOCTYPE ".getBytes(StandardCharsets.UTF_8), 0);
    if (start == -1) return bytes;
    int end = find(bytes, new byte[]{'>'}, start + 10) + 1;
    if (end <= start) return bytes;
    String doctype = new String(Arrays.copyOfRange(bytes, start, end), StandardCharsets.UTF_8);
    if (!doctype.contains("-//Berlioz") && !doctype.contains("//Weborganic")) return bytes;
    byte[] clean = new byte[bytes.length - (end - start)];
    System.arraycopy(bytes, 0, clean, 0, start);
    System.arraycopy(bytes, end, clean, start, bytes.length - end);
    return clean;
  }

  private static int find(byte[] bytes, byte[] target, int from) {
    for (int i = Math.max(0, from); i <= bytes.length - target.length; i++) {
      if (Arrays.equals(bytes, i, i + target.length, target, 0, target.length)) return i;
    }
    return -1;
  }

  private static SAXParser newParser() throws ParserConfigurationException, SAXException {
    SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setValidating(false);
    factory.setXIncludeAware(false);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    return factory.newSAXParser();
  }

  private static String message(SAXException ex) {
    String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    if (ex instanceof SAXParseException && ((SAXParseException) ex).getLineNumber() > 0) {
      message = "Line " + ((SAXParseException) ex).getLineNumber() + ": " + message;
    }
    return Targets.bound(message, MAX_MESSAGE);
  }

  /** Stops the parser at the bound on elements. */
  private static final class TooManyElements extends SAXException {

    private static final long serialVersionUID = 1L;

    TooManyElements() {
      super("More than " + MAX_ELEMENTS + " elements");
    }

  }

  /** Collects the rules, as Berlioz's handlers: elements by local name, attributes by name. */
  private static final class Handler extends DefaultHandler {

    private final FilterType type;

    private final List<Rule> rules = new ArrayList<>();

    private int elements;

    private int total;

    Handler(FilterType type) {
      this.type = type;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
      if (++this.elements > MAX_ELEMENTS) throw new TooManyElements();
      if (!this.type.id().equals(localName)) return;
      this.total++;
      if (this.rules.size() < MAX_RULES) {
        this.rules.add(new Rule(atts.getValue("from"), atts.getValue("to"), atts.getValue("permanent")));
      }
    }

  }

}
