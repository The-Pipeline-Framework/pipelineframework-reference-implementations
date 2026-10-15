package org.pipelineframework.search.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.pipelineframework.search.common.cache.CrawlCacheKeyStrategy;
import org.pipelineframework.search.common.cache.ParseCacheKeyStrategy;
import org.pipelineframework.search.common.canonical.SearchTransportRecordAdapter;
import org.pipelineframework.search.common.domain.CrawlRequest;
import org.pipelineframework.search.common.domain.RawDocument;
import org.pipelineframework.context.PipelineContext;

class SearchTransportRecordAdapterTest {
  @Test
  void unsupportedTransportRecordIsNotAdapted() {
    assertTrue(SearchTransportRecordAdapter.toEntity(new FetchHeaderDto("X-Test", "yes")).isEmpty());
  }

  @Test
  void generatedSourceRecordRetainsLegacyCacheIdentity() {
    UUID docId = UUID.randomUUID();
    CrawlRequestDto request = new CrawlRequestDto(docId, "https://example.com", "GET", "text/html",
        "en", Optional.of("public"), List.of(new FetchHeaderDto("X-Test", "yes")));
    CrawlRequest entity = (CrawlRequest) SearchTransportRecordAdapter.toEntity(request).orElseThrow();
    assertEquals(docId, entity.docId);
    assertEquals("yes", entity.fetchHeaders.get("X-Test"));

    CrawlCacheKeyStrategy strategy = new CrawlCacheKeyStrategy();
    PipelineContext context = mock(PipelineContext.class);
    assertEquals(strategy.resolveKey(entity, context), strategy.resolveKey(request, context));
    assertTrue(strategy.supportsTarget(RawDocumentDto.class));
    assertTrue(strategy.supportsTarget(org.pipelineframework.search.common.canonical.RawDocument.class));
  }

  @Test
  void generatedOutputRecordRetainsPersistenceAndCacheIdentity() {
    UUID docId = UUID.randomUUID();
    RawDocumentDto output = new RawDocumentDto(docId, "https://example.com", "method=GET",
        "content", "sha256", Instant.parse("2026-01-01T00:00:00Z"));
    RawDocument entity = (RawDocument) SearchTransportRecordAdapter.toEntity(output).orElseThrow();
    assertEquals(docId, entity.docId);
    assertEquals("sha256", entity.rawContentHash);
    PipelineContext context = mock(PipelineContext.class);
    assertEquals(new ParseCacheKeyStrategy().resolveKey(entity, context),
        new ParseCacheKeyStrategy().resolveKey(output, context));
  }
}
