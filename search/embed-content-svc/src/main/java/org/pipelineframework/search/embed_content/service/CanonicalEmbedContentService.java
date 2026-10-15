package org.pipelineframework.search.embed_content.service;

import jakarta.enterprise.context.ApplicationScoped;
import io.smallrye.mutiny.Uni;
import org.pipelineframework.search.common.canonical.TokenBatch;
import org.pipelineframework.search.common.canonical.EmbeddedChunk;
import org.pipelineframework.service.ReactiveService;

/** Adapts the existing service to the canonical v3 records. */
@ApplicationScoped
public class CanonicalEmbedContentService implements ReactiveService<TokenBatch, EmbeddedChunk> {
  private final ProcessEmbedContentService delegate;

  public CanonicalEmbedContentService(ProcessEmbedContentService delegate) {
    this.delegate = delegate;
  }

  @Override
  public Uni<EmbeddedChunk> process(TokenBatch input) {
    return delegate.process(input.toEntity()).onItem().transform(EmbeddedChunk::fromEntity);
  }
}
