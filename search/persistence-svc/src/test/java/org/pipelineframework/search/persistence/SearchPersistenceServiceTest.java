package org.pipelineframework.search.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.smallrye.mutiny.Uni;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.pipelineframework.search.common.canonical.RawDocument;

class SearchPersistenceServiceTest {
  @Test
  void persistsEntityAndReturnsCanonicalValue() {
    RawDocument input = new RawDocument(UUID.randomUUID(), "https://example.com", "method=GET",
        "content", "hash", Instant.parse("2026-01-01T00:00:00Z"));
    RecordingService service = new RecordingService();

    assertSame(input, service.process(input).await().indefinitely());
    org.pipelineframework.search.common.domain.RawDocument persisted =
        (org.pipelineframework.search.common.domain.RawDocument) service.persisted;
    assertEquals(input.docId(), persisted.docId);
    assertEquals(input.rawContentHash(), persisted.rawContentHash);
  }

  private static final class RecordingService extends SearchPersistenceService<RawDocument> {
    private Object persisted;

    @Override
    protected <R> Uni<R> persistRepresentation(R representation) {
      persisted = representation;
      return Uni.createFrom().item(representation);
    }
  }
}
