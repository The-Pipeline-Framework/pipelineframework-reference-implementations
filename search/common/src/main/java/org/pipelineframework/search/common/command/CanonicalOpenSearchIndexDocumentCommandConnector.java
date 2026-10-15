package org.pipelineframework.search.common.command;

import jakarta.enterprise.context.ApplicationScoped;
import io.smallrye.mutiny.Uni;
import org.pipelineframework.command.CommandConnector;
import org.pipelineframework.command.CommandRequest;
import org.pipelineframework.search.common.canonical.SearchIndexDocument;
import org.pipelineframework.search.common.canonical.SearchIndexWriteResult;

/** Command boundary for the modular v3 Search contract. */
@ApplicationScoped
public class CanonicalOpenSearchIndexDocumentCommandConnector
    implements CommandConnector<SearchIndexDocument, SearchIndexWriteResult> {
  public static final String COMMAND = "opensearch-index-document-v3";

  private final OpenSearchIndexDocumentCommandConnector delegate;

  public CanonicalOpenSearchIndexDocumentCommandConnector(OpenSearchIndexDocumentCommandConnector delegate) {
    this.delegate = delegate;
  }

  @Override
  public String command() {
    return COMMAND;
  }

  @Override
  public Uni<SearchIndexWriteResult> execute(CommandRequest<SearchIndexDocument> request) {
    CommandRequest<org.pipelineframework.search.common.domain.SearchIndexDocument> adapted =
        new CommandRequest<>(request.descriptor(), request.commandId(), request.occurrenceId(),
            request.attemptId(), request.input().toEntity(), request.executionContext(),
            request.config(), request.callbackContext());
    return delegate.execute(adapted).onItem().transform(SearchIndexWriteResult::fromEntity);
  }
}
