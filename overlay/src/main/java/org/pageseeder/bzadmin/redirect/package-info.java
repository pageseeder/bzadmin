/**
 * The Redirects view of the Config page: the registrations of Berlioz's {@code RedirectFilter} and
 * {@code RelocationFilter}, and the rules that each filter loaded, from Berlioz's diagnostic snapshot
 * ({@code docs/design/redirects.md}).
 *
 * <p>{@link org.pageseeder.bzadmin.redirect.FilterRegistrations} finds the registrations,
 * {@link org.pageseeder.bzadmin.redirect.GetRedirectFilters} matches them to the snapshot by filter
 * name and reports them, with the warnings of {@link org.pageseeder.bzadmin.redirect.Targets}.
 */
@org.jspecify.annotations.NullMarked
package org.pageseeder.bzadmin.redirect;
