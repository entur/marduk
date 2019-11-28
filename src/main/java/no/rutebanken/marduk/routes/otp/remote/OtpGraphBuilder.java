package no.rutebanken.marduk.routes.otp.remote;

public interface OtpGraphBuilder {
    void build(String otpWorkDir, boolean buildBaseGraph, String timestamp);
}
