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
