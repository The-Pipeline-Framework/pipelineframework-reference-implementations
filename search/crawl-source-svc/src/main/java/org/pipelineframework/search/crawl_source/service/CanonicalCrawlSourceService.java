package org.pipelineframework.search.crawl_source.service;

import jakarta.enterprise.context.ApplicationScoped;
import io.smallrye.mutiny.Uni;
import org.pipelineframework.search.common.canonical.CrawlRequest;
import org.pipelineframework.search.common.canonical.RawDocument;
import org.pipelineframework.service.ReactiveService;

/** Adapts the existing crawler to the canonical v3 records. */
@ApplicationScoped
public class CanonicalCrawlSourceService implements ReactiveService<CrawlRequest, RawDocument> {
  private final ProcessCrawlSourceService delegate;

  public CanonicalCrawlSourceService(ProcessCrawlSourceService delegate) {
    this.delegate = delegate;
  }

  @Override
  public Uni<RawDocument> process(CrawlRequest input) {
    return delegate.process(input.toEntity()).onItem().transform(RawDocument::fromEntity);
  }
}
