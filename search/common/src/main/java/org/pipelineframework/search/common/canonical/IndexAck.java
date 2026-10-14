package org.pipelineframework.search.common.canonical;

import java.time.Instant;
import java.util.UUID;

/** Canonical Search value at the pipeline boundary. */
public record IndexAck(
    UUID docId,
    String indexVersion,
    String tokensHash,
    Integer tokenBatchCount,
    Integer uniqueTokenCount,
    String topToken,
    Instant indexedAt,
    Boolean success
) {
  public static IndexAck fromEntity(org.pipelineframework.search.common.domain.IndexAck source) {
    return new IndexAck(
        source.docId,
        source.getIndexVersion(),
        source.getTokensHash(),
        source.getTokenBatchCount(),
        source.getUniqueTokenCount(),
        source.getTopToken(),
        source.getIndexedAt(),
        source.getSuccess());
  }

  public org.pipelineframework.search.common.domain.IndexAck toEntity() {
    org.pipelineframework.search.common.domain.IndexAck result = new org.pipelineframework.search.common.domain.IndexAck();
    result.docId = docId;
    result.setIndexVersion(indexVersion);
    result.setTokensHash(tokensHash);
    result.setTokenBatchCount(tokenBatchCount);
    result.setUniqueTokenCount(uniqueTokenCount);
    result.setTopToken(topToken);
    result.setIndexedAt(indexedAt);
    result.setSuccess(success);
    return result;
  }
}
