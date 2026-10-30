/**
 * A read-only repository of the PSML files of the host application, with bounded work.
 *
 * <p>A {@link org.pageseeder.bzadmin.psml.PsmlRepository} has named roots; a request names a folder
 * or a document by its path in a root, which {@link org.pageseeder.bzadmin.psml.PsmlRoot} confines
 * to it: no symbolic link, hidden name or parent segment, and only {@code .psml} files.
 */
@org.jspecify.annotations.NullMarked
package org.pageseeder.bzadmin.psml;
