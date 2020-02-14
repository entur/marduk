package no.rutebanken.marduk.security.oauth2;

public class Message {
    private final String message;

    Message(String message) {
        this.message = message;
    }

    public String getMessage() {
        return this.message;
    }
}