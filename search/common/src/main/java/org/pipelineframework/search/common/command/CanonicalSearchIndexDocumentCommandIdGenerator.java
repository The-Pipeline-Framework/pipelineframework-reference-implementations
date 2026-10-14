package org.pipelineframework.search.common.command;

import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.command.CommandDescriptor;
import org.pipelineframework.command.CommandIdGenerator;
import org.pipelineframework.search.common.canonical.SearchIndexDocument;

/** Reuses the established stable Search write identity for the v3 record. */
@ApplicationScoped
public class CanonicalSearchIndexDocumentCommandIdGenerator
    implements CommandIdGenerator<SearchIndexDocument> {
  private final SearchIndexDocumentCommandIdGenerator delegate;

  public CanonicalSearchIndexDocumentCommandIdGenerator(SearchIndexDocumentCommandIdGenerator delegate) {
    this.delegate = delegate;
  }

  @Override
  public String commandId(CommandDescriptor descriptor, SearchIndexDocument input) {
    return delegate.commandId(descriptor, input.toEntity());
  }
}
