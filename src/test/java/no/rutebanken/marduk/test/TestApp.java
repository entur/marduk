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

package no.rutebanken.marduk.test;

import no.rutebanken.marduk.App;

import org.apache.camel.test.spring.CamelSpringTestContextLoaderTestExecutionListener;
import org.apache.camel.test.spring.CamelTestContextBootstrapper;
import org.apache.camel.test.spring.DisableJmxTestExecutionListener;
import org.apache.camel.test.spring.StopWatchTestExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootApplication
@BootstrapWith(CamelTestContextBootstrapper.class)
@TestExecutionListeners(value = {CamelSpringTestContextLoaderTestExecutionListener.class, DisableJmxTestExecutionListener.class, StopWatchTestExecutionListener.class}, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class TestApp extends App {

    private static Logger logger = LoggerFactory.getLogger(TestApp.class);

    public static void main(String... args){
        App.main(args);
    }

    @Override
    protected void waitForProviderRepository() throws InterruptedException {
    	
    }
}
