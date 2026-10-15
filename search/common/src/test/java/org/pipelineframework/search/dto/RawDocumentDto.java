package org.pipelineframework.search.dto;

import java.time.Instant;
import java.util.UUID;

public record RawDocumentDto(UUID docId, String sourceUrl, String fetchOptions, String rawContent,
    String rawContentHash, Instant fetchedAt) {}
