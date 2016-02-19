package no.rutebanken.marduk.routes.otp;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GraphBuilderClientTest {

    @Test
    public void buildGraphWithNullDirectory() {
        assertThatThrownBy(() -> new GraphBuilderClient().buildGraph(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("null is not a directory.")
                .hasNoCause();
    }

    @Test
    public void buildGraphWithNonexistingDirectory() {
        assertThatThrownBy(() -> new GraphBuilderClient().buildGraph(new File("bogus")) )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bogus is not a directory.")
                .hasNoCause();
    }

    @Test
    public void buildGraphWithFile() throws IOException {
        File temp = File.createTempFile("bogus", "tmp", new File("/tmp"));
        assertThatThrownBy(() -> new GraphBuilderClient().buildGraph(temp) )
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
        new GraphBuilderClient().buildGraph(directory);
        assertThat(new File(directory, "Graph.obj")).exists().isFile();
    }

    private void deleteIfExists(File file) {
        if (file.exists()){
            file.delete();
        }
    }

}