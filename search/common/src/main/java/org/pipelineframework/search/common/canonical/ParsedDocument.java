package org.pipelineframework.search.common.canonical;

import java.time.Instant;
import java.util.UUID;

/** Canonical Search value at the pipeline boundary. */
public record ParsedDocument(
    UUID docId,
    String title,
    String content,
    String contentHash,
    String rawContentHash,
    Instant extractedAt
) {
  public static ParsedDocument fromEntity(org.pipelineframework.search.common.domain.ParsedDocument source) {
    return new ParsedDocument(
        source.docId,
        source.title,
        source.content,
        source.contentHash,
        source.rawContentHash,
        source.extractedAt);
  }

  public org.pipelineframework.search.common.domain.ParsedDocument toEntity() {
    org.pipelineframework.search.common.domain.ParsedDocument result = new org.pipelineframework.search.common.domain.ParsedDocument();
    result.docId = docId;
    result.title = title;
    result.content = content;
    result.contentHash = contentHash;
    result.rawContentHash = rawContentHash;
    result.extractedAt = extractedAt;
    return result;
  }
}
