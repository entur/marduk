package no.rutebanken.marduk.routes.otp;

import com.google.common.base.Joiner;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static no.rutebanken.marduk.Constants.*;

/**
 * Trigger graph
 */
@Component
public class OtpGraphRouteBuilder extends BaseRouteBuilder {

    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:OtpGraphQueue")
                .log(LoggingLevel.INFO, getClass().getName(), "About to trigger OTP graph building.")
                .toD("https://jenkins.rutebanken.org/job/otpgraph/buildWithParameters?CORRELATION_ID=${header." + PROVIDER_ID)  //TODO figure out a proper id to use
                .log(LoggingLevel.DEBUG, getClass().getName(), "Result from queue");


        //Posting to queue from command line:        curl -XPOST -d "body=testing, 123" http://admin:admin@localhost:8161/api/message?destination=queue://queue:OtpGraphStatusQueue
        from("activemq:queue:OtpGraphStatusQueue")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Got OTP graph building status.")
                .process(e -> {
                    final Stream<String> splitPairs = Stream.of(e.getIn().getBody(String.class).split(","));
                    Map<String, String> result = splitPairs.collect(Collectors.<String, String, String>toMap(s -> s.split(":")[0], s -> s.split(":")[1]));
                    Joiner.MapJoiner mapJoiner = Joiner.on(',').withKeyValueSeparator("=");
                    System.out.println(mapJoiner.join(result));
                });

    }
}
