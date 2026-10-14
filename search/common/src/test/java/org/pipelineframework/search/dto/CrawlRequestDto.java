package org.pipelineframework.search.dto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record CrawlRequestDto(UUID docId, String sourceUrl, String fetchMethod, String accept,
    String acceptLanguage, Optional<String> authScope, List<FetchHeaderDto> fetchHeaders) {}
