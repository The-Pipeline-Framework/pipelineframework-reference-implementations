package org.pipelineframework.search.tokenize_content.service;

import jakarta.enterprise.context.ApplicationScoped;
import io.smallrye.mutiny.Multi;
import org.pipelineframework.search.common.canonical.ParsedDocument;
import org.pipelineframework.search.common.canonical.TokenBatch;
import org.pipelineframework.service.ReactiveStreamingService;

/** Adapts the existing service to the canonical v3 records. */
@ApplicationScoped
public class CanonicalTokenizeContentService implements ReactiveStreamingService<ParsedDocument, TokenBatch> {
  private final ProcessTokenizeContentService delegate;

  public CanonicalTokenizeContentService(ProcessTokenizeContentService delegate) {
    this.delegate = delegate;
  }

  @Override
  public Multi<TokenBatch> process(ParsedDocument input) {
    return delegate.process(input.toEntity()).onItem().transform(TokenBatch::fromEntity);
  }
}
