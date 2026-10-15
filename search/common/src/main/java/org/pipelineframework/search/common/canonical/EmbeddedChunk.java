package org.pipelineframework.search.common.canonical;

import java.time.Instant;
import java.util.UUID;

/** Canonical Search value at the pipeline boundary. */
public record EmbeddedChunk(
    UUID docId,
    Integer batchIndex,
    Integer tokenCount,
    String tokens,
    String tokensHash,
    String contentHash,
    String vectorHash,
    String vectorVersion,
    Instant embeddedAt
) {
  public static EmbeddedChunk fromEntity(org.pipelineframework.search.common.domain.EmbeddedChunk source) {
    return new EmbeddedChunk(
        source.docId,
        source.batchIndex,
        source.tokenCount,
        source.tokens,
        source.tokensHash,
        source.contentHash,
        source.vectorHash,
        source.vectorVersion,
        source.embeddedAt);
  }

  public org.pipelineframework.search.common.domain.EmbeddedChunk toEntity() {
    org.pipelineframework.search.common.domain.EmbeddedChunk result = new org.pipelineframework.search.common.domain.EmbeddedChunk();
    result.docId = docId;
    result.batchIndex = batchIndex;
    result.tokenCount = tokenCount;
    result.tokens = tokens;
    result.tokensHash = tokensHash;
    result.contentHash = contentHash;
    result.vectorHash = vectorHash;
    result.vectorVersion = vectorVersion;
    result.embeddedAt = embeddedAt;
    return result;
  }
}
