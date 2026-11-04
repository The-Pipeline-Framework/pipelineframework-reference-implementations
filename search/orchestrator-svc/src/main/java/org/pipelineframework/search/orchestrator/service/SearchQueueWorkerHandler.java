package org.pipelineframework.search.orchestrator.service;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.Quarkus;
import org.pipelineframework.config.pipeline.PipelineJson;
import org.pipelineframework.orchestrator.ExecutionWorkItem;
import org.pipelineframework.orchestrator.PipelineOrchestratorConfig;
import org.pipelineframework.orchestrator.PipelineReleaseIdentityResolver;
import org.pipelineframework.orchestrator.SqsInboundMessage;
import org.pipelineframework.orchestrator.SqsMessageDisposition;
import org.pipelineframework.orchestrator.SqsWorkItemAction;
import org.pipelineframework.orchestrator.release.LocalPipelineReleaseActivation;

/** Runs the standard TPF SQS work action inside the Lambda invocation. */
public final class SearchQueueWorkerHandler {
    public SearchQueueWorkerHandler() {
        Quarkus.manualInitialize();
    }

    public void handleRequest(Map<String, Object> input, Context context) throws IOException {
        Quarkus.manualStart();
        JsonNode records = PipelineJson.mapper().valueToTree(input).path("Records");
        if (!records.isArray() || records.size() != 1) {
            throw new IOException("Search SQS worker requires exactly one message per invocation");
        }
        JsonNode record = records.get(0);
        String body = record.required("body").asText();
        ExecutionWorkItem work = PipelineJson.mapper().readValue(body, ExecutionWorkItem.class);
        SqsInboundMessage message = new SqsInboundMessage(
            Optional.of(record.required("messageId").asText()), Optional.of(body));
        Duration budget = Duration.ofMillis(Math.max(1, context.getRemainingTimeInMillis() - 1000));
        var container = Arc.container();
        var config = container.instance(PipelineOrchestratorConfig.class).get();
        var identity = container.instance(PipelineReleaseIdentityResolver.class).get();
        var activation = container.instance(LocalPipelineReleaseActivation.class).get();
        var action = container.instance(SqsWorkItemAction.class).get();
        // Each Lambda has its own in-memory release registry. Install the same
        // packaged release for the submitting tenant before decoding its work.
        SqsMessageDisposition disposition = activation.activateForCurrentRelease(
                work.tenantId(), identity.pipelineId(config), identity.contractVersion(), identity.releaseVersion(config))
            .chain(() -> action.handle(message)).await().atMost(budget);
        if (disposition != SqsMessageDisposition.ACKNOWLEDGE) {
            throw new IOException("Search SQS work item needs redelivery");
        }
    }
}
