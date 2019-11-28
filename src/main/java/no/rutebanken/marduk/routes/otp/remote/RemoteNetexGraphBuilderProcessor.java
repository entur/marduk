package no.rutebanken.marduk.routes.otp.remote;


import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"otp-invm-graph-builder", "otp-kubernetes-job-graph-builder"})
public class RemoteNetexGraphBuilderProcessor extends AbstractRemoteGraphBuilderProcessor {

    public RemoteNetexGraphBuilderProcessor() {
        super(false);
    }
}
