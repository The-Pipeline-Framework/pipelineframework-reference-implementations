package org.pipelineframework.search.common.canonical;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Adapts generated v3 transport records to Search's existing entity-backed plugins. */
public final class SearchTransportRecordAdapter {
  private static final String TRANSPORT_PACKAGE = "org.pipelineframework.search.dto.";

  private SearchTransportRecordAdapter() {
  }

  public static boolean matchesTarget(Class<?> targetType, String canonicalName) {
    return targetType.getName().equals(TRANSPORT_PACKAGE + canonicalName + "Dto")
        || targetType.getName().equals("org.pipelineframework.search.common.canonical." + canonicalName);
  }

  public static Optional<Object> toEntity(Object value) {
    if (value instanceof CrawlRequest request) return Optional.of(request.toEntity());
    if (value instanceof RawDocument document) return Optional.of(document.toEntity());
    if (value instanceof ParsedDocument document) return Optional.of(document.toEntity());
    if (value instanceof TokenBatch batch) return Optional.of(batch.toEntity());
    if (value instanceof EmbeddedChunk chunk) return Optional.of(chunk.toEntity());
    if (value instanceof SearchIndexDocument document) return Optional.of(document.toEntity());
    if (value instanceof SearchIndexWriteResult result) return Optional.of(result.toEntity());
    if (value instanceof IndexAck ack) return Optional.of(ack.toEntity());
    if (value == null || !value.getClass().isRecord()
        || !value.getClass().getName().startsWith(TRANSPORT_PACKAGE)) {
      return Optional.empty();
    }
    String name = value.getClass().getSimpleName();
    if (name.equals("CrawlRequestDto")) {
      return Optional.of(crawlRequest(value));
    }
    Class<?> canonicalType = switch (name) {
      case "RawDocumentDto" -> RawDocument.class;
      case "ParsedDocumentDto" -> ParsedDocument.class;
      case "TokenBatchDto" -> TokenBatch.class;
      case "EmbeddedChunkDto" -> EmbeddedChunk.class;
      case "SearchIndexDocumentDto" -> SearchIndexDocument.class;
      case "SearchIndexWriteResultDto" -> SearchIndexWriteResult.class;
      case "IndexAckDto" -> IndexAck.class;
      default -> throw new IllegalArgumentException("Unsupported Search transport record: " + name);
    };
    return Optional.of(convert(value, canonicalType));
  }

  private static Object convert(Object transport, Class<?> canonicalType) {
    try {
      RecordComponent[] fields = canonicalType.getRecordComponents();
      RecordComponent[] sourceFields = transport.getClass().getRecordComponents();
      if (fields.length != sourceFields.length) {
        throw new IllegalArgumentException("Search transport record has a different field count: "
            + transport.getClass().getName());
      }
      Object[] arguments = new Object[fields.length];
      for (int index = 0; index < fields.length; index++) {
        if (!fields[index].getName().equals(sourceFields[index].getName())
            || fields[index].getType() != sourceFields[index].getType()) {
          throw new IllegalArgumentException("Search transport record field mismatch: "
              + transport.getClass().getName() + "." + fields[index].getName());
        }
        arguments[index] = sourceFields[index].getAccessor().invoke(transport);
      }
      Class<?>[] parameterTypes = Arrays.stream(fields).map(RecordComponent::getType).toArray(Class<?>[]::new);
      Constructor<?> constructor = canonicalType.getDeclaredConstructor(parameterTypes);
      Object canonical = constructor.newInstance(arguments);
      return canonicalType.getMethod("toEntity").invoke(canonical);
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException("Cannot adapt Search transport record " + transport.getClass().getName(), error);
    }
  }

  private static org.pipelineframework.search.common.domain.CrawlRequest crawlRequest(Object transport) {
    org.pipelineframework.search.common.domain.CrawlRequest result =
        new org.pipelineframework.search.common.domain.CrawlRequest();
    result.docId = field(transport, "docId", UUID.class).orElseThrow();
    result.sourceUrl = field(transport, "sourceUrl", String.class).orElse("");
    result.fetchMethod = field(transport, "fetchMethod", String.class).orElse("");
    result.accept = field(transport, "accept", String.class).orElse("");
    result.acceptLanguage = field(transport, "acceptLanguage", String.class).orElse("");
    Optional<?> authScope = field(transport, "authScope", Optional.class).orElse(Optional.empty());
    result.authScope = authScope.map(Object::toString).orElse("");
    Map<String, String> headers = new LinkedHashMap<>();
    List<?> fetchHeaders = field(transport, "fetchHeaders", List.class).orElse(List.of());
    for (Object header : fetchHeaders) {
      headers.put(field(header, "name", String.class).orElseThrow(),
          field(header, "value", String.class).orElseThrow());
    }
    result.fetchHeaders = headers;
    return result;
  }

  private static <T> Optional<T> field(Object record, String name, Class<T> type) {
    try {
      Object value = record.getClass().getMethod(name).invoke(record);
      return Optional.ofNullable(type.cast(value));
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException("Cannot read Search transport field " + name, error);
    }
  }
}
