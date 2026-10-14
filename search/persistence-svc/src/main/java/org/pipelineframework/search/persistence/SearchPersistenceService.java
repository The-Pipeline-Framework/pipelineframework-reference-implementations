package org.pipelineframework.search.persistence;

import io.smallrye.mutiny.Uni;
import org.pipelineframework.plugin.persistence.PersistenceService;
import org.pipelineframework.search.common.canonical.SearchTransportRecordAdapter;

/** Persists Search's entity representation while retaining the canonical transport value. */
public class SearchPersistenceService<T> extends PersistenceService<T> {
  @Override
  public Uni<T> process(T item) {
    return SearchTransportRecordAdapter.toEntity(item)
        .map(entity -> persistRepresentation(entity).replaceWith(item))
        .orElseGet(() -> super.process(item));
  }
}
