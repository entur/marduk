package no.rutebanken.marduk.leader;

/**
 * Whether this pod should run the work that must happen on exactly one pod.
 *
 * <p>Replaces {@code camel-master}, which marduk used through {@code BaseRouteBuilder.singletonFrom()}.
 *
 * <p>Two differences from what camel-master did, both deliberate:
 *
 * <ul>
 *   <li><b>One lease, not ten.</b> camel-master created a Lease per lock name - verified off the 4.21.0
 *       jar: {@code NativeLeaseResourceManager} names the Lease after the lock group, lowercased, so
 *       marduk has ten of them and leadership for different routes can sit on different pods. A single
 *       lease concentrates all singleton work on one of the two pods instead. That is simpler to reason
 *       about and, with the periodic jobs marduk has, cheap.
 *   <li><b>Losing leadership does not stop work already running.</b> camel-master stopped its child
 *       consumer on leadership loss. A flag that callers check cannot do that. So leader-gated work has to
 *       be safe to finish after the lease has moved, which is the reason nothing that keeps its state in
 *       the leader's heap should be gated this way.
 * </ul>
 *
 * <p>The flag is a decision from up to a heartbeat ago. Callers should treat a false negative as cheap -
 * one skipped periodic run - and never rely on it for mutual exclusion of anything that matters.
 */
public interface LeaderElection {

    boolean isLeader();
}
