package org.pipelineframework.search.common.canonical;

import java.time.Instant;
import java.util.UUID;

/** Canonical crawl result. */
public record RawDocument(
    UUID docId,
    String sourceUrl,
    String fetchOptions,
    String rawContent,
    String rawContentHash,
    Instant fetchedAt
) {
  public static RawDocument fromEntity(org.pipelineframework.search.common.domain.RawDocument source) {
    return new RawDocument(source.docId, source.sourceUrl, source.fetchOptions,
        source.rawContent, source.rawContentHash, source.fetchedAt);
  }

  public org.pipelineframework.search.common.domain.RawDocument toEntity() {
    org.pipelineframework.search.common.domain.RawDocument result =
        new org.pipelineframework.search.common.domain.RawDocument();
    result.docId = docId;
    result.sourceUrl = sourceUrl;
    result.fetchOptions = fetchOptions;
    result.rawContent = rawContent;
    result.rawContentHash = rawContentHash;
    result.fetchedAt = fetchedAt;
    return result;
  }
}
