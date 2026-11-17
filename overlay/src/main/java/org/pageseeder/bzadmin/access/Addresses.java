package org.pageseeder.bzadmin.access;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.jspecify.annotations.Nullable;

/**
 * The parsing of the numeric addresses of a connection ({@code getRemoteAddr()},
 * {@code getLocalAddr()}) and of a host requested: never resolved as a name.
 */
final class Addresses {

  private Addresses() {
  }

  /**
   * @param address An address, possibly between brackets ({@code [::1]}).
   * @return the address without its brackets.
   */
  static String unbracket(String address) {
    return address.startsWith("[") && address.endsWith("]") ? address.substring(1, address.length() - 1) : address;
  }

  /**
   * @param literal An address without brackets.
   * @return the IPv6 address (or IPv4-mapped one) when the literal is one, or {@code null}: only a
   *   literal with a colon and the characters of an IPv6 address is parsed, so nothing is looked up.
   */
  static @Nullable InetAddress ipv6(String literal) {
    if (literal.indexOf(':') < 0 || !literal.matches("[0-9A-Fa-f:.]+(%[0-9A-Za-z]+)?")) return null;
    try {
      return InetAddress.getByName(literal);
    } catch (UnknownHostException ex) {
      return null;
    }
  }

}
