package no.rutebanken.marduk.routes.otp;

import org.opentripplanner.graph_builder.GraphBuilder;
import org.opentripplanner.standalone.CommandLineParameters;

import java.io.File;

public class GraphBuilderClient {

    public void buildGraph(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()){
            throw new IllegalArgumentException(directory + " is not a directory.");
        }
        CommandLineParameters commandLineParameters = new CommandLineParameters();
        GraphBuilder graphBuilder = GraphBuilder.forDirectory(commandLineParameters, directory);
        graphBuilder.run();
    }
}
