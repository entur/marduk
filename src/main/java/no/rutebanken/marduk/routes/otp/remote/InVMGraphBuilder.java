package no.rutebanken.marduk.routes.otp.remote;


import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.repository.BlobStoreRepository;
import no.rutebanken.marduk.routes.otp.GraphBuilderClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;

@Component
@Profile("otp-invm-graph-builder")
public class InVMGraphBuilder implements OtpGraphBuilder {

    @Value("${otp.netex.graph.build.directory:files/otpgraph/netex}")
    private String otpGraphBuildDirectory;

    @Autowired
    private BlobStoreRepository blobStoreRepository;

    @Override
    public void build(String otpWorkDir, boolean buildBaseGraph, String timestamp) {

        Path localWorkDirectory = Path.of(otpGraphBuildDirectory).resolve(otpWorkDir);
        localWorkDirectory.toFile().mkdirs();
        GraphBuilderClient graphBuilderClient = new GraphBuilderClient();
        graphBuilderClient.buildGraph(localWorkDirectory.toFile(),  buildBaseGraph, !buildBaseGraph);
        try (FileInputStream fis = new FileInputStream(localWorkDirectory.resolve("GraphObj").toFile())) {
            blobStoreRepository.uploadBlob(otpWorkDir + "/" + Constants.GRAPH_OBJ, fis, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
