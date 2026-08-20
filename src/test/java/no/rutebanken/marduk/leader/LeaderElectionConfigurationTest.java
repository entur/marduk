package no.rutebanken.marduk.leader;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * That the always-leader implementation cannot be reached by accident.
 *
 * <p>Two replicas both believing they lead runs every scheduled job and every batch twice, and says nothing
 * about it. A dropped or mistyped ConfigMap key is the cheap way to get there.
 */
class LeaderElectionConfigurationTest {

    private static final String IN_A_POD = "KUBERNETES_SERVICE_HOST";

    private final LeaderElectionConfiguration configuration = new LeaderElectionConfiguration();

    @Test
    void aPodWithoutTheKeyRefusesToStart() {
        MockEnvironment environment = new MockEnvironment().withProperty(IN_A_POD, "10.0.0.1");

        assertThrows(IllegalStateException.class,
                () -> configuration.singleNodeLeaderElection(environment));
    }

    @Test
    void aPodThatDeliberatelyTurnsElectionOffGetsTheSingleNodeImplementation() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(IN_A_POD, "10.0.0.1")
                .withProperty(LeaderElectionConfiguration.KUBERNETES_ENABLED, "false");

        assertInstanceOf(SingleNodeLeaderElection.class,
                configuration.singleNodeLeaderElection(environment));
    }

    @Test
    void outsideAPodTheFallbackNeedsNoProperty() {
        assertInstanceOf(SingleNodeLeaderElection.class,
                configuration.singleNodeLeaderElection(new MockEnvironment()));
    }

    @Test
    void aPodWithAMistypedValueRefusesToStartToo() {
        // Spring's condition sends a value that is neither true nor false to the kubernetes implementation,
        // so this is belt and braces: reaching the always-leader one in a pod needs an explicit false.
        MockEnvironment environment = new MockEnvironment()
                .withProperty(IN_A_POD, "10.0.0.1")
                .withProperty(LeaderElectionConfiguration.KUBERNETES_ENABLED, "tru");

        assertThrows(IllegalStateException.class,
                () -> configuration.singleNodeLeaderElection(environment),
                "a mistyped value must not reach the always-leader implementation either");
    }
}
