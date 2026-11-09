/**
 * The Redirects view of the Config page: the registrations of Berlioz's {@code RedirectFilter} and
 * {@code RelocationFilter}, and the rules of their configuration files, read without changing the
 * filters ({@code docs/design/redirects.md}).
 *
 * <p>{@link org.pageseeder.bzadmin.redirect.FilterRegistrations} finds the registrations,
 * {@link org.pageseeder.bzadmin.redirect.MappingFile} reads a file within {@code WEB-INF} and the
 * bounds, {@link org.pageseeder.bzadmin.redirect.MappingParser} parses it as Berlioz does, and
 * {@link org.pageseeder.bzadmin.redirect.GetRedirectFilters} reports them.
 */
@org.jspecify.annotations.NullMarked
package org.pageseeder.bzadmin.redirect;
