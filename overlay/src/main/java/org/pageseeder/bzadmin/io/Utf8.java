package org.pageseeder.bzadmin.io;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * UTF-8 decoding for bounded source previews.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class Utf8 {

  private Utf8() {
  }

  /**
   * Decodes UTF-8, replacing malformed input; when truncated, an incomplete character at the end is dropped.
   *
   * @param bytes The source bytes; the buffer position advances past the decoded bytes.
   * @param truncated Whether more source bytes were omitted.
   * @return the decoded text.
   */
  public static String decode(ByteBuffer bytes, boolean truncated) {
    CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE);
    CharBuffer text = CharBuffer.allocate(bytes.remaining());
    decoder.decode(bytes, text, !truncated);
    if (!truncated) decoder.flush(text);
    text.flip();
    return text.toString();
  }

}
