package org.pipelineframework.search.index_document.service;

import jakarta.enterprise.context.ApplicationScoped;
import io.smallrye.mutiny.Uni;
import org.pipelineframework.search.common.canonical.EmbeddedChunk;
import org.pipelineframework.search.common.canonical.SearchIndexDocument;
import org.pipelineframework.service.ReactiveService;

/** Adapts the existing service to the canonical v3 records. */
@ApplicationScoped
public class CanonicalBuildSearchIndexDocumentService implements ReactiveService<EmbeddedChunk, SearchIndexDocument> {
  private final ProcessBuildSearchIndexDocumentService delegate;

  public CanonicalBuildSearchIndexDocumentService(ProcessBuildSearchIndexDocumentService delegate) {
    this.delegate = delegate;
  }

  @Override
  public Uni<SearchIndexDocument> process(EmbeddedChunk input) {
    return delegate.process(input.toEntity()).onItem().transform(SearchIndexDocument::fromEntity);
  }
}
