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

package no.rutebanken.marduk.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Kubernetes configmap against re-introducing the dual-cluster-service startup crash.
 *
 * <p>camel-file-cluster-service-starter and camel-kubernetes-cluster-service-starter both register a
 * CamelClusterService by default, but the {@code master:} component (used by
 * {@code BaseRouteBuilder.singletonFrom()}) needs exactly one. In Kubernetes the file lock must stay
 * disabled; if the line is dropped, pods crash-loop at startup with
 * "Cannot auto create component: master".
 *
 * <p>The Spring integration tests cannot catch this because they boot the mirror-image profile
 * (kubernetes off, file on) from {@code src/test/resources/application.properties}, so the invariant is
 * asserted directly on the configmap template, which is where the regression actually lives.
 */
class ClusterServiceConfigMapTest {

    private static final Path CONFIGMAP = Path.of("helm/marduk/templates/configmap.yaml");

    @Test
    void kubernetesConfigMapDisablesFileClusterService() throws IOException {
        String configmap = Files.readString(CONFIGMAP);
        assertTrue(configmap.contains("camel.cluster.file.enabled=false"),
                "The k8s configmap (" + CONFIGMAP + ") must disable the file cluster service so the master: "
                        + "component has exactly one CamelClusterService; without it every singletonFrom() route "
                        + "fails at startup with 'Cannot auto create component: master'.");
    }
}
