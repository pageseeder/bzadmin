package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Verifies the documented setup with no explicit filter dispatchers. */
final class RequestOnlyHostTest {

  @Test
  void appliesAccessAndOperationPolicyToDirectRequests() throws Exception {
    try (BerliozHost host = BerliozHost.start("/", "web-request-only.xml")) {
      for (String format : new String[]{".html", ".src", ".json"}) {
        assertEquals(200, host.get("/bzadmin/config" + format).statusCode(), format);
        assertEquals(200, host.as("viewer").get("/bzadmin/config" + format).statusCode(), format);
        assertEquals(403, host.as(null).get("/bzadmin/config" + format).statusCode(), format);
        assertEquals(403, host.as("user").get("/bzadmin/config" + format).statusCode(), format);
      }
      assertEquals(403, host.as("viewer").post("/bzadmin/api/config/reload.src", "berlioz-reload=true").statusCode());
      assertEquals(403, host.send("POST", "/bzadmin/api/config/reload.src", "berlioz-reload=true").statusCode());
      assertEquals(200, host.post("/bzadmin/api/config/reload.src", "berlioz-reload=true").statusCode());
    }
  }
}
