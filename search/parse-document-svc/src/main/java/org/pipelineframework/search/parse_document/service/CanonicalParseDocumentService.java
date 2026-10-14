package org.pipelineframework.search.parse_document.service;

import jakarta.enterprise.context.ApplicationScoped;
import io.smallrye.mutiny.Uni;
import org.pipelineframework.search.common.canonical.RawDocument;
import org.pipelineframework.search.common.canonical.ParsedDocument;
import org.pipelineframework.service.ReactiveService;

/** Adapts the existing service to the canonical v3 records. */
@ApplicationScoped
public class CanonicalParseDocumentService implements ReactiveService<RawDocument, ParsedDocument> {
  private final ProcessParseDocumentService delegate;

  public CanonicalParseDocumentService(ProcessParseDocumentService delegate) {
    this.delegate = delegate;
  }

  @Override
  public Uni<ParsedDocument> process(RawDocument input) {
    return delegate.process(input.toEntity()).onItem().transform(ParsedDocument::fromEntity);
  }
}
