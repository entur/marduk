package no.rutebanken.marduk.routes.otp.remote;


import no.rutebanken.marduk.routes.otp.GraphBuilderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Build the OTP graph in-vm, for testing purpose.
 */
@Component
@Profile("otp-invm-graph-builder")
public class InVMGraphBuilder implements OtpGraphBuilder {

    private static final Logger logger = LoggerFactory.getLogger(InVMGraphBuilder.class);


    @Value("${otp.netex.graph.build.directory:files/otpgraph/netex}")
    private String otpGraphBuildDirectory;

    @Value("${otp.netex.graph.build.config:}")
    private String otpGraphBuildConfig;

    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreGraphSubdirectory;

    @Override
    public void build(String otpWorkDir, boolean buildBaseGraph, String timestamp) {

        logger.info("Building graph in directory {} with timestamp {}.", otpWorkDir, timestamp);

        Path localWorkDirectory = Path.of(otpGraphBuildDirectory).resolve(otpWorkDir);
        localWorkDirectory.toFile().mkdirs();


        try {
            String configFileAsString = new String(Files.readAllBytes(Path.of(otpGraphBuildConfig)), StandardCharsets.UTF_8);
            configFileAsString = configFileAsString.replace("${OTP_GCS_WORK_DIR}", otpWorkDir);

            if (buildBaseGraph) {
                configFileAsString = configFileAsString.replace("${OTP_GCS_BASE_GRAPH_DIR}", otpWorkDir);
            } else {
                configFileAsString = configFileAsString.replace("${OTP_GCS_BASE_GRAPH_DIR}", blobStoreGraphSubdirectory);
            }

            Files.write(localWorkDirectory.resolve("build-config.json"), configFileAsString.getBytes());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        GraphBuilderClient graphBuilderClient = new GraphBuilderClient();
        graphBuilderClient.buildGraph(localWorkDirectory.toFile(), buildBaseGraph, !buildBaseGraph);

    }
}
