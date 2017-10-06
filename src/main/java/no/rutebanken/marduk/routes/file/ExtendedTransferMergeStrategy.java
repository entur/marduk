package no.rutebanken.marduk.routes.file;

import org.onebusaway.gtfs.model.Transfer;
import org.onebusaway.gtfs_merge.strategies.TransferMergeStrategy;

import java.util.Objects;

/**
 * Extension of the default Merge strategy for Transfers that also compares the extension fields (from_trip, to_trip. from_route,to_route)
 */
public class ExtendedTransferMergeStrategy extends TransferMergeStrategy {

    @Override
    protected boolean entitiesAreIdentical(Transfer transferA, Transfer transferB) {
        if (!Objects.equals(transferA.getFromRoute(), transferB.getFromRoute())) {
            return false;
        }
        if (!Objects.equals(transferA.getToRoute(), transferB.getToRoute())){
            return false;
        }
        if (!Objects.equals(transferA.getFromTrip(), transferB.getFromTrip())) {
            return false;
        }
        if (!Objects.equals(transferA.getToTrip(), transferB.getToTrip())) {
            return false;
        }
        return super.entitiesAreIdentical(transferA, transferB);
    }
}
