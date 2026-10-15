package org.pipelineframework.search.common.canonical;

import java.time.Instant;
import java.util.UUID;

/** Canonical Search value at the pipeline boundary. */
public record SearchIndexWriteResult(
    UUID docId,
    String externalId,
    String commandId,
    String indexName,
    String resultStatus,
    Boolean createdOrUpdated,
    Boolean recordedDuplicate,
    Integer batchIndex,
    Integer tokenCount,
    String tokens,
    String tokensHash,
    String vectorHash,
    String vectorVersion,
    Instant indexedAt
) {
  public static SearchIndexWriteResult fromEntity(org.pipelineframework.search.common.domain.SearchIndexWriteResult source) {
    return new SearchIndexWriteResult(
        source.docId,
        source.externalId,
        source.commandId,
        source.indexName,
        source.resultStatus,
        source.createdOrUpdated,
        source.recordedDuplicate,
        source.batchIndex,
        source.tokenCount,
        source.tokens,
        source.tokensHash,
        source.vectorHash,
        source.vectorVersion,
        source.indexedAt);
  }

  public org.pipelineframework.search.common.domain.SearchIndexWriteResult toEntity() {
    org.pipelineframework.search.common.domain.SearchIndexWriteResult result = new org.pipelineframework.search.common.domain.SearchIndexWriteResult();
    result.docId = docId;
    result.externalId = externalId;
    result.commandId = commandId;
    result.indexName = indexName;
    result.resultStatus = resultStatus;
    result.createdOrUpdated = createdOrUpdated;
    result.recordedDuplicate = recordedDuplicate;
    result.batchIndex = batchIndex;
    result.tokenCount = tokenCount;
    result.tokens = tokens;
    result.tokensHash = tokensHash;
    result.vectorHash = vectorHash;
    result.vectorVersion = vectorVersion;
    result.indexedAt = indexedAt;
    return result;
  }
}
