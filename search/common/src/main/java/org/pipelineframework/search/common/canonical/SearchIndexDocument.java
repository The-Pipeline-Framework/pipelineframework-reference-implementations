package org.pipelineframework.search.common.canonical;

import java.time.Instant;
import java.util.UUID;

/** Canonical Search value at the pipeline boundary. */
public record SearchIndexDocument(
    UUID docId,
    String externalId,
    Integer batchIndex,
    Integer tokenCount,
    String tokens,
    String tokensHash,
    String contentHash,
    String vectorHash,
    String vectorVersion,
    String indexName,
    Instant preparedAt
) {
  public static SearchIndexDocument fromEntity(org.pipelineframework.search.common.domain.SearchIndexDocument source) {
    return new SearchIndexDocument(
        source.docId,
        source.externalId,
        source.batchIndex,
        source.tokenCount,
        source.tokens,
        source.tokensHash,
        source.contentHash,
        source.vectorHash,
        source.vectorVersion,
        source.indexName,
        source.preparedAt);
  }

  public org.pipelineframework.search.common.domain.SearchIndexDocument toEntity() {
    org.pipelineframework.search.common.domain.SearchIndexDocument result = new org.pipelineframework.search.common.domain.SearchIndexDocument();
    result.docId = docId;
    result.externalId = externalId;
    result.batchIndex = batchIndex;
    result.tokenCount = tokenCount;
    result.tokens = tokens;
    result.tokensHash = tokensHash;
    result.contentHash = contentHash;
    result.vectorHash = vectorHash;
    result.vectorVersion = vectorVersion;
    result.indexName = indexName;
    result.preparedAt = preparedAt;
    return result;
  }
}
