/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.routes.otp;

import org.opentripplanner.graph_builder.GraphBuilder;
import org.opentripplanner.standalone.CommandLineParameters;

import java.io.File;

public class GraphBuilderClient {

    public void buildGraph(File directory, boolean skipTransit, boolean loadBaseGraph) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException(directory + " is not a directory.");
        }
        CommandLineParameters commandLineParameters = new CommandLineParameters();

        commandLineParameters.skipTransit = skipTransit;
        commandLineParameters.loadBaseGraph = loadBaseGraph;
        commandLineParameters.build = directory;
        GraphBuilder graphBuilder = GraphBuilder.forDirectory(commandLineParameters, directory);
        graphBuilder.run();
    }
}
