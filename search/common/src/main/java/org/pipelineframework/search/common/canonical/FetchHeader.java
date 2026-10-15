package org.pipelineframework.search.common.canonical;

/** One request header in the canonical Search source contract. */
public record FetchHeader(String name, String value) {}
