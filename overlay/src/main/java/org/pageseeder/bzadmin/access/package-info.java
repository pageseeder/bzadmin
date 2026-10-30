/**
 * Access to the admin services: the host's grant ({@link org.pageseeder.bzadmin.access.AdminAccess}),
 * the filter that holds the access policy ({@link org.pageseeder.bzadmin.access.AdminFilter}), and
 * the check of its verdict by every generator ({@link org.pageseeder.bzadmin.access.AdminGuard}).
 *
 * <p>Fail-closed: without a grant, or when the filter did not process the request, every admin
 * service is denied and no generator produces data.
 */
@org.jspecify.annotations.NullMarked
package org.pageseeder.bzadmin.access;
