package org.pipelineframework.search.common.canonical;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.pipelineframework.type.CanonicalFieldValue;

class CrawlRequestTest {
  @Test
  void preservesFetchOptionsAtTheLegacyCrawlerBoundary() {
    UUID docId = UUID.randomUUID();
    CrawlRequest request = new CrawlRequest(docId, "https://example.test", "GET", "text/html",
        "en-US", CanonicalFieldValue.of("tenant-a"),
        List.of(new FetchHeader("X-Trace", "trace-1")));

    var entity = request.toEntity();

    assertEquals(docId, entity.docId);
    assertEquals("tenant-a", entity.authScope);
    assertEquals(Map.of("X-Trace", "trace-1"), entity.fetchHeaders);
  }
}
