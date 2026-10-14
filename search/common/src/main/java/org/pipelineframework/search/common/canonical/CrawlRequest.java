package org.pipelineframework.search.common.canonical;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.pipelineframework.type.CanonicalFieldValue;

/** Canonical source value; the JPA entity stays behind the service boundary. */
public record CrawlRequest(
    UUID docId,
    String sourceUrl,
    String fetchMethod,
    String accept,
    String acceptLanguage,
    CanonicalFieldValue<String> authScope,
    List<FetchHeader> fetchHeaders
) {
  public CrawlRequest {
    fetchHeaders = List.copyOf(fetchHeaders);
  }

  public org.pipelineframework.search.common.domain.CrawlRequest toEntity() {
    org.pipelineframework.search.common.domain.CrawlRequest result =
        new org.pipelineframework.search.common.domain.CrawlRequest();
    result.docId = docId;
    result.sourceUrl = sourceUrl;
    result.fetchMethod = fetchMethod;
    result.accept = accept;
    result.acceptLanguage = acceptLanguage;
    result.authScope = authScope.asOptional().orElse("");
    Map<String, String> headers = new LinkedHashMap<>();
    fetchHeaders.forEach(header -> headers.put(header.name(), header.value()));
    result.fetchHeaders = headers;
    return result;
  }
}
