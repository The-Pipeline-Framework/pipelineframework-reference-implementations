import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.pipelineframework.config.pipeline.PipelineJson;
import org.pipelineframework.orchestrator.PipelineOrchestratorConfig;
import org.pipelineframework.orchestrator.release.DynamoPipelineReleaseRegistry;
import org.pipelineframework.orchestrator.release.PipelineReleaseDescriptor;
import org.pipelineframework.orchestrator.release.PipelineReleaseRecord;
import org.pipelineframework.orchestrator.release.PipelineContractDescriptor;
import org.pipelineframework.orchestrator.release.PipelineContractDescriptorLoader;
import org.pipelineframework.orchestrator.release.PipelineReleaseDescriptorValidator;
import org.pipelineframework.orchestrator.release.PipelineReleaseStatus;

/** Deployment-side registration for the disposable Search AWS fixture. */
class RegisterSearchRelease {
    public static void main(String[] args) {
        try {
            register(args);
            System.exit(0);
        } catch (Exception error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void register(String[] args) throws Exception {
        var mapper = PipelineJson.mapper();
        var deployment = mapper.readTree(Path.of(args[0]).toFile());
        String version = deployment.required("version").asText();
        String table = deployment.required("table").asText();
        String tenant = "search-aws-e2e";
        String truth = "orchestrator-svc";
        String contractPath = "META-INF/pipeline/pipeline-contract.json";

        ObjectNode descriptor = mapper.createObjectNode();
        var artifacts = descriptor.putArray("artifacts");
        PipelineContractDescriptor compiledContract;
        String pipelineId;
        var deployedArtifacts = deployment.required("artifacts");
        var deployedTruth = deployedArtifacts.required(truth);
        Path truthArchive = Path.of(deployedTruth.required("local_path").asText());
        try (var zip = new ZipFile(truthArchive.toFile());
             var input = zip.getInputStream(zip.getEntry(contractPath))) {
            compiledContract = new PipelineContractDescriptorLoader().load(input);
            var contract = mapper.valueToTree(compiledContract);
            pipelineId = contract.required("pipelineId").asText();
            descriptor.put("schemaVersion", PipelineReleaseDescriptor.CURRENT_SCHEMA_VERSION)
                .put("pipelineId", pipelineId)
                .put("contractVersion", contract.required("contractVersion").asText())
                .put("releaseVersion", version)
                .put("compiledTruthArtifactId", truth);
            var capabilities = new LinkedHashSet<String>();
            if (contract.path("capabilities").path("localTransitionExecution").asBoolean()) {
                capabilities.add("local");
            }
            contract.path("capabilities").path("transitionWorkerProtocols").forEach(p -> capabilities.add(p.asText()));
            for (var entry : deployedArtifacts.properties()) {
                String name = entry.getKey();
                Path archive = Path.of(entry.getValue().required("local_path").asText());
                String digest = entry.getValue().required("digest").asText();
                if (!digest.equals(checksum(archive))) {
                    throw new IllegalStateException("Deployed ZIP digest differs for " + name);
                }
                var artifact = artifacts.addObject()
                    .put("artifactId", name)
                    .put("kind", "lambda-zip")
                    // This disposable release validates against local build inputs. The
                    // registry stores the deployed primary artifact's S3 location separately.
                    .put("uri", archive.toUri().toASCIIString())
                    .put("digest", digest);
                var steps = artifact.putArray("stepIds");
                var protocols = artifact.putArray("capabilities");
                if ("function-worker".equals(name)) {
                    contract.required("steps").forEach(step -> steps.add(step.required("authoredName").asText()));
                    capabilities.forEach(protocols::add);
                }
            }
        }

        long now = System.currentTimeMillis();
        var deployedDescriptor = mapper.treeToValue(descriptor, PipelineReleaseDescriptor.class);
        new PipelineReleaseDescriptorValidator().validate(deployedDescriptor, compiledContract);
        String digest = deployedTruth.required("digest").asText();
        var record = new PipelineReleaseRecord(tenant, pipelineId, compiledContract.contractVersion(), version,
            PipelineReleaseStatus.REGISTERED, deployedDescriptor, truth, digest, deployedTruth.required("uri").asText(),
            Files.size(truthArchive), digest, compiledContract, now, now, 0L);
        // This deployment tool trusts the Terraform table, independent of application config sources.
        var config = new SmallRyeConfigBuilder()
            .withDefaultValue("pipeline.orchestrator.dynamo.release-table", table)
            .withMapping(PipelineOrchestratorConfig.class).build().getConfigMapping(PipelineOrchestratorConfig.class);
        var registry = new DynamoPipelineReleaseRegistry(config);
        var budget = Duration.ofSeconds(30);
        registry.register(record).await().atMost(budget);
        registry.activate(tenant, pipelineId, version, now).await().atMost(budget).orElseThrow();
        var active = registry.active(tenant, pipelineId).await().atMost(budget).orElseThrow();
        if (!version.equals(active.releaseVersion())) {
            throw new IllegalStateException("Search activated a different release than the deployed package set");
        }
        System.out.println("Registered and activated Search release " + version);
    }

    private static String checksum(Path path) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }
}
