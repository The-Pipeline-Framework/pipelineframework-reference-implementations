/*
 * Copyright (c) 2023-2026 Mariano Barcia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.pipelineframework.search.orchestrator.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration tests for the modular Search Pipeline deployed to AWS Lambda.
 *
 * This test validates:
 * - HTTP submission and result polling through the orchestrator Function URL
 * - SQS-backed execution across the modular Search topology
 * - Cardinality handling (ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE)
 * - Response serialization/deserialization across the Lambda HTTP bridge
 *
 * Prerequisites:
 * - The queue-backed Search modular topology must already be deployed
 * - AWS_LAMBDA_ORCHESTRATOR_URL env var or aws.lambda.orchestrator.url system property must be set
 */
class AwsLambdaModularEndToEndIT {

    private static final Logger LOG = Logger.getLogger(AwsLambdaModularEndToEndIT.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static String orchestratorUrl;
    private static HttpClient httpClient;

    @BeforeAll
    static void setup() {
        orchestratorUrl = System.getenv("AWS_LAMBDA_ORCHESTRATOR_URL");
        if (orchestratorUrl == null || orchestratorUrl.isBlank()) {
            orchestratorUrl = System.getProperty("aws.lambda.orchestrator.url");
        }

        org.junit.jupiter.api.Assumptions.assumeTrue(orchestratorUrl != null && !orchestratorUrl.isBlank(),
            "Skipping AWS Lambda modular E2E tests: neither AWS_LAMBDA_ORCHESTRATOR_URL env var nor aws.lambda.orchestrator.url system property is set");

        if (orchestratorUrl.endsWith("/")) {
            orchestratorUrl = orchestratorUrl.substring(0, orchestratorUrl.length() - 1);
        }

        LOG.infof("Testing modular AWS Lambda deployment at: %s", orchestratorUrl);

        httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        verifyConnectivity();
    }

    @Test
    void testPipelineRunUnaryInvocation() throws Exception {
        UUID testDocId = UUID.randomUUID();
        String requestBody = OBJECT_MAPPER.writeValueAsString(createCrawlRequest(testDocId,
            "https://example.com/aws-unary-" + testDocId));

        JsonNode responseJson = invokePipeline(requestBody, Duration.ofSeconds(120));

        assertTrue(responseJson.has("docId"), "Response should contain docId");
        assertEquals(testDocId.toString(), responseJson.get("docId").asText(),
            "Response docId should match request");
    }

    @Test
    void testFanOutFanInCardinality() throws Exception {
        UUID testDocId = UUID.randomUUID();
        ObjectNode request = createCrawlRequest(testDocId, "https://example.com/aws-fanout-" + testDocId);
        request.put("content", generateLongTestContent());

        JsonNode responseJson = invokePipeline(OBJECT_MAPPER.writeValueAsString(request), Duration.ofSeconds(180));

        assertTrue(responseJson.has("docId"), "Response should contain docId");
        assertTrue(responseJson.has("indexVersion"), "Response should contain indexVersion");
        assertTrue(responseJson.has("tokenBatchCount"), "Response should contain tokenBatchCount");
        assertTrue(responseJson.has("success"), "Response should contain success flag");
        assertTrue(responseJson.get("success").asBoolean(), "Success flag should be true");
        assertTrue(responseJson.get("tokenBatchCount").asInt() > 0,
            "Should have processed at least one token batch");
    }

    @Test
    void testMultipleSequentialInvocations() throws Exception {
        for (int i = 0; i < 3; i++) {
            UUID testDocId = UUID.randomUUID();
            String requestBody = OBJECT_MAPPER.writeValueAsString(createCrawlRequest(testDocId,
                "https://example.com/aws-seq-" + testDocId));

            JsonNode responseJson = invokePipeline(requestBody, Duration.ofSeconds(120));

            assertTrue(responseJson.has("success"), "Response should contain success flag");
            assertTrue(responseJson.get("success").asBoolean(), "Success flag should be true");
        }
    }

    private static void verifyConnectivity() {
        org.junit.jupiter.api.Assumptions.assumeTrue(orchestratorUrl != null && !orchestratorUrl.isBlank(),
            "Skipping modular AWS suite because AWS_LAMBDA_ORCHESTRATOR_URL not configured");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(orchestratorUrl + "/q/health/live"))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean isHealthy = response.statusCode() >= 200 && response.statusCode() < 300;
            assertTrue(isHealthy,
                "Modular AWS suite failed: health endpoint returned HTTP " + response.statusCode());
        } catch (Exception e) {
            fail("Modular AWS suite failed: health endpoint unreachable: " + e.getMessage(), e);
        }
    }

    private JsonNode invokePipeline(String requestBody, Duration timeout) throws Exception {
        String tenant = "search-aws-e2e";
        long deadline = System.nanoTime() + timeout.toNanos();
        HttpRequest submission = HttpRequest.newBuilder(URI.create(orchestratorUrl + "/pipeline/run-async"))
            .timeout(Duration.ofSeconds(20))
            .header("x-tenant-id", tenant)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        HttpResponse<String> accepted = httpClient.send(submission, HttpResponse.BodyHandlers.ofString());
        assertTrue(accepted.statusCode() == 200 || accepted.statusCode() == 202, accepted.body());
        String executionId = OBJECT_MAPPER.readTree(accepted.body()).path("executionId").asText();
        assertTrue(!executionId.isBlank(), "Submission must return an execution ID");
        String executionUrl = orchestratorUrl + "/pipeline/executions/" + executionId;
        JsonNode status = OBJECT_MAPPER.createObjectNode();
        while (System.nanoTime() < deadline) {
            status = getExecutionJson(executionUrl, tenant);
            switch (status.path("status").asText()) {
                case "SUCCEEDED":
                    return getExecutionJson(executionUrl + "/result", tenant);
                case "FAILED", "DLQ", "CANCELLED", "REMOTE_OUTCOME_UNKNOWN":
                    fail("Search execution " + executionId + " ended unsuccessfully: " + status);
                    break;
                default:
                    Thread.sleep(500);
            }
        }
        return fail("Search execution " + executionId + " did not finish: " + status);
    }

    private JsonNode getExecutionJson(String url, String tenant) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("x-tenant-id", tenant)
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return OBJECT_MAPPER.readTree(response.body());
    }

    private ObjectNode createCrawlRequest(UUID docId, String sourceUrl) {
        ObjectNode request = OBJECT_MAPPER.createObjectNode()
            .put("docId", docId.toString())
            .put("sourceUrl", sourceUrl)
            .put("fetchMethod", "GET")
            .put("accept", "text/html")
            .put("acceptLanguage", "en-US");
        request.putArray("fetchHeaders");
        return request;
    }

    private String generateLongTestContent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("This is a test paragraph number ").append(i)
                .append(" with some sample text for tokenization purposes. ")
                .append("The quick brown fox jumps over the lazy dog. ")
                .append("Lorem ipsum dolor sit amet, consectetur adipiscing elit. ");
        }
        return sb.toString();
    }
}
