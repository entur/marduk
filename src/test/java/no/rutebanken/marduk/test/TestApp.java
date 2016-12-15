package no.rutebanken.marduk.test;

import no.rutebanken.marduk.App;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TestApp extends App {

    private static Logger logger = LoggerFactory.getLogger(TestApp.class);

    public static void main(String... args){
        App.main(args);
    }

    @Override
    protected void waitForProviderRepository() throws InterruptedException {
        // NOOP
    }
}
