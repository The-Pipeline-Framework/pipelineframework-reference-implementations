package org.pipelineframework.search.common.mapper;

import org.pipelineframework.objectingest.ObjectSnapshot;
import org.pipelineframework.objectingest.ObjectSnapshotMapper;
import org.pipelineframework.search.common.canonical.RawDocument;

/** Maps admitted object snapshots to the v3 Search input record. */
public final class CanonicalSearchObjectDocumentMapper implements ObjectSnapshotMapper<RawDocument> {
  private final SearchObjectDocumentMapper delegate = new SearchObjectDocumentMapper();

  @Override
  public RawDocument map(ObjectSnapshot snapshot) {
    return RawDocument.fromEntity(delegate.map(snapshot));
  }
}
