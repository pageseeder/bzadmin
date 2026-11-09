package org.pageseeder.bzadmin.system;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.pageseeder.berlioz.util.Redaction;

/**
 * The redaction of system property values: by name, as Berlioz does, and by value, for the secrets
 * that the name does not reveal.
 *
 * <ul>
 *   <li>a sensitive name ({@link Redaction#isSensitiveName(String)}): the whole value;</li>
 *   <li>{@code sun.java.command}: the program arguments, after the main class or JAR;</li>
 *   <li>the user information of a URL anywhere in the value: its password, or all of it without
 *   {@code :};</li>
 *   <li>the value of a parameter with a sensitive name, after {@code ?}, {@code &}, {@code ;} or at
 *   the start of the value.</li>
 * </ul>
 *
 * <p>Paths and user names are not redacted.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
final class SystemProperties {

  /** What replaces a sensitive value, as Berlioz writes it. */
  static final String REDACTED = Redaction.REDACTED;

  /** The property with the main class or JAR of the JVM and the program arguments. */
  static final String JAVA_COMMAND = "sun.java.command";

  /**
   * The user information of a URL: after '://', up to the last '@' of the authority, which ends
   * with '/', '?', '#' or whitespace (a password can contain '@').
   */
  private static final Pattern USER_INFO = Pattern.compile("://([^/?#\\s]*)@");

  /** A parameter at the start of the value, or after '?', '&' or ';'. */
  private static final Pattern PARAMETER = Pattern.compile("(^|[?&;])([^=?&;#\\s]+)=([^&;#\\s]*)");

  /** The end of the JAR path in the command: '.jar' followed by whitespace or the end. */
  private static final Pattern JAR_END = Pattern.compile("\\.jar(?=\\s|$)");

  private SystemProperties() {
  }

  /**
   * @param name  The name of the property.
   * @param value Its value.
   * @return the value with its sensitive parts replaced by {@link #REDACTED}.
   */
  static String redact(String name, String value) {
    if (Redaction.isSensitiveName(name)) return REDACTED;
    String redacted = JAVA_COMMAND.equals(name) ? command(value) : value;
    return parameters(userInfo(redacted));
  }

  /**
   * @param value The value of {@code sun.java.command}: the main class or the path of a JAR, then the
   *              program arguments, separated by spaces.
   * @return the main class or JAR, followed by {@code [REDACTED]} when there are arguments.
   */
  static String command(String value) {
    String command = value.strip();
    Matcher jar = JAR_END.matcher(command.toLowerCase(Locale.ROOT));
    int end = jar.find() ? jar.end() : firstWhitespace(command);
    if (end < 0 || command.substring(end).isBlank()) return command;
    return command.substring(0, end) + ' ' + REDACTED;
  }

  /**
   * @param value Any value.
   * @return the value without the password of a URL, or its user information without password.
   */
  static String userInfo(String value) {
    if (!value.contains("://")) return value;
    Matcher m = USER_INFO.matcher(value);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String info = m.group(1);
      int colon = info.indexOf(':');
      String kept = colon >= 0 ? info.substring(0, colon + 1) : "";
      m.appendReplacement(out, Matcher.quoteReplacement("://" + kept + REDACTED + '@'));
    }
    m.appendTail(out);
    return out.toString();
  }

  /**
   * @param value Any value.
   * @return the value without the values of the parameters with a sensitive name.
   */
  static String parameters(String value) {
    if (value.indexOf('=') < 0) return value;
    Matcher m = PARAMETER.matcher(value);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String parameter = m.group(2);
      String replaced = Redaction.isSensitiveName(parameter) && !m.group(3).isEmpty()
          ? m.group(1) + parameter + '=' + REDACTED
          : m.group();
      m.appendReplacement(out, Matcher.quoteReplacement(replaced));
    }
    m.appendTail(out);
    return out.toString();
  }

  private static int firstWhitespace(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (Character.isWhitespace(value.charAt(i))) return i;
    }
    return -1;
  }

}
