package org.pipelineframework.search.index_document.service;

import jakarta.enterprise.context.ApplicationScoped;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;
import org.pipelineframework.search.common.canonical.SearchIndexWriteResult;
import org.pipelineframework.search.common.canonical.IndexAck;
import org.pipelineframework.service.ReactiveStreamingClientService;

/** Adapts the existing service to the canonical v3 records. */
@ApplicationScoped
public class CanonicalSummarizeIndexWritesService implements ReactiveStreamingClientService<SearchIndexWriteResult, IndexAck> {
  private final ProcessSummarizeIndexWritesService delegate;

  public CanonicalSummarizeIndexWritesService(ProcessSummarizeIndexWritesService delegate) {
    this.delegate = delegate;
  }

  @Override
  public Uni<IndexAck> process(Multi<SearchIndexWriteResult> input) {
    return delegate.process(input.onItem().transform(SearchIndexWriteResult::toEntity)).onItem().transform(IndexAck::fromEntity);
  }
}
