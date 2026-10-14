package org.pipelineframework.search.orchestrator.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.Entity;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.pipelineframework.search.common.canonical.SearchTransportRecordAdapter;

class SearchV3TransportAdapterTest {
  @Test
  void everyGeneratedOutputRecordHasAnEntityRepresentation() throws Exception {
    Path generatedClasses = Path.of("target/classes-pipeline/orchestrator-client").toAbsolutePath();
    try (URLClassLoader loader = new URLClassLoader(new java.net.URL[] {generatedClasses.toUri().toURL()},
        getClass().getClassLoader())) {
      for (String name : List.of("RawDocument", "ParsedDocument", "TokenBatch", "EmbeddedChunk",
          "SearchIndexDocument", "SearchIndexWriteResult", "IndexAck")) {
        Class<?> dtoType = Class.forName("org.pipelineframework.search.dto." + name + "Dto", true, loader);
        var fields = dtoType.getRecordComponents();
        Class<?>[] types = new Class<?>[fields.length];
        Object[] values = new Object[fields.length];
        for (int index = 0; index < fields.length; index++) {
          types[index] = fields[index].getType();
          values[index] = sample(types[index]);
        }
        Object output = dtoType.getConstructor(types).newInstance(values);
        Object entity = SearchTransportRecordAdapter.toEntity(output).orElseThrow();
        assertTrue(entity.getClass().isAnnotationPresent(Entity.class), name);
      }
    }
  }

  private Object sample(Class<?> type) {
    if (type == UUID.class) return UUID.randomUUID();
    if (type == Instant.class) return Instant.parse("2026-01-01T00:00:00Z");
    if (type == String.class) return "value";
    if (type == Integer.class) return 1;
    if (type == Boolean.class) return true;
    if (type == List.class) return List.of();
    if (type == Optional.class) return Optional.empty();
    throw new IllegalArgumentException("Unrecognized generated Search field type: " + type);
  }
}
