package no.rutebanken.marduk.logger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.JsonWritingUtils;
import net.logstash.logback.composite.loggingevent.LogLevelJsonProvider;

import java.io.IOException;

public class CustomLogLevelJsonProvider extends LogLevelJsonProvider {

    final static String DEBUG = "DEBUG";
    final static String ERROR = "ERROR";
    final static String INFO = "INFO";
    final static String WARNING = "WARNING";

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event)
            throws IOException {
        JsonWritingUtils.writeStringField(generator, "severity",
                getCustomLogLevel(event));
    }

    private String getCustomLogLevel(ILoggingEvent event) {
        if (event.getLevel() == Level.ALL) {
            return Level.ALL.toString();
        }
        if (event.getLevel() == Level.DEBUG) {
            return DEBUG;
        }
        if (event.getLevel() == Level.ERROR) {
            return ERROR;
        }
        if (event.getLevel() == Level.INFO) {
            return INFO;
        }
        if (event.getLevel() == Level.WARN) {
            return WARNING;
        }
        return "";
    }
}

