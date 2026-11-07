package org.pageseeder.bzadmin.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

final class InstallationCheckTest {

  private static final Set<String> EXTENSIONS = Set.of("/", "*.html", "*.xml", "*.json");

  private static final Set<String> PREFIXES = Set.of("/", "/html/*", "/xml/*");

  @Test
  void acceptsTheSupportedSetups() {
    assertEquals(List.of(), InstallationCheck.servletProblems(EXTENSIONS));
    assertEquals(List.of(), InstallationCheck.servletProblems(PREFIXES));
    assertEquals(List.of(), InstallationCheck.filterProblems(EXTENSIONS, List.of("/bzadmin/*")));
    assertEquals(List.of(), InstallationCheck.filterProblems(PREFIXES, List.of("/html/bzadmin/*", "/xml/bzadmin/*")));
  }

  @Test
  void reportsTheMissingXmlMapping() {
    assertProblem(InstallationCheck.servletProblems(Set.of("*.html")), "mapped to *.html without *.xml");
    assertProblem(InstallationCheck.servletProblems(Set.of("/html/*")), "mapped to /html/* without /xml/*");
  }

  @Test
  void reportsABerliozWithoutHtmlMapping() {
    assertProblem(InstallationCheck.servletProblems(Set.of("/", "/berlioz/*")), "Berlioz is not mapped");
  }

  @Test
  void reportsTheAdminServicesOutOfTheFilter() {
    assertProblem(InstallationCheck.filterProblems(EXTENSIONS, List.of("/admin/*")), "not mapped to /bzadmin/*");
    assertProblem(InstallationCheck.filterProblems(PREFIXES, List.of("/html/bzadmin/*")), "not mapped to /xml/bzadmin/*");
    assertProblem(InstallationCheck.filterProblems(PREFIXES, List.of("/xml/bzadmin/*")), "not mapped to /html/bzadmin/*");
  }

  @Test
  void acceptsWiderFilterMappings() {
    assertEquals(List.of(), InstallationCheck.filterProblems(EXTENSIONS, List.of("/*")));
    assertEquals(List.of(), InstallationCheck.filterProblems(PREFIXES, List.of("/html/*", "/xml/*")));
  }

  private static void assertProblem(List<String> problems, String expected) {
    assertEquals(1, problems.size(), problems.toString());
    assertTrue(problems.get(0).contains(expected), problems.get(0));
  }

}
