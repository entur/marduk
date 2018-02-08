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

import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GraphBuilderClientTest {

    @Test
    public void buildGraphWithNullDirectory() {
        assertThatThrownBy(() -> new GraphBuilderClient().buildGraph(null,false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("null is not a directory.")
                .hasNoCause();
    }

    @Test
    public void buildGraphWithNonexistingDirectory() {
        assertThatThrownBy(() -> new GraphBuilderClient().buildGraph(new File("bogus"),false, false) )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bogus is not a directory.")
                .hasNoCause();
    }

    @Test
    public void buildGraphWithFile() throws IOException {
        File temp = File.createTempFile("bogus", "tmp", new File("/tmp"));
        assertThatThrownBy(() -> new GraphBuilderClient().buildGraph(temp, false, false) )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith(" is not a directory.")
                .hasNoCause();
    }

    @Test
    public void buildGraph() {
        File directory = new File("target/test-classes/no/rutebanken/marduk/routes/otp");
        File graphObjectFile = new File(directory, "Graph.obj");
        deleteIfExists(graphObjectFile);
        assertThat(graphObjectFile).doesNotExist();
        new GraphBuilderClient().buildGraph(directory,false, false);
        assertThat(new File(directory, "Graph.obj")).exists().isFile();
    }

    private void deleteIfExists(File file) {
        if (file.exists()){
            file.delete();
        }
    }

}