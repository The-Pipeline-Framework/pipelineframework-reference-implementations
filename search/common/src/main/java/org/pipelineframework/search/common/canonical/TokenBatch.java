package org.pipelineframework.search.common.canonical;

import java.time.Instant;
import java.util.UUID;

/** Canonical Search value at the pipeline boundary. */
public record TokenBatch(
    UUID docId,
    Integer batchIndex,
    Integer tokenCount,
    String tokens,
    String tokensHash,
    String contentHash,
    Instant tokenizedAt
) {
  public static TokenBatch fromEntity(org.pipelineframework.search.common.domain.TokenBatch source) {
    return new TokenBatch(
        source.docId,
        source.batchIndex,
        source.tokenCount,
        source.tokens,
        source.tokensHash,
        source.contentHash,
        source.tokenizedAt);
  }

  public org.pipelineframework.search.common.domain.TokenBatch toEntity() {
    org.pipelineframework.search.common.domain.TokenBatch result = new org.pipelineframework.search.common.domain.TokenBatch();
    result.docId = docId;
    result.batchIndex = batchIndex;
    result.tokenCount = tokenCount;
    result.tokens = tokens;
    result.tokensHash = tokensHash;
    result.contentHash = contentHash;
    result.tokenizedAt = tokenizedAt;
    return result;
  }
}
