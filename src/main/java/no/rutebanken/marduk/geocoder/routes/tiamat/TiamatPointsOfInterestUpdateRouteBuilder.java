package no.rutebanken.marduk.geocoder.routes.tiamat;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// TODO specific per source?
@Component
public class TiamatPointsOfInterestUpdateRouteBuilder extends BaseRouteBuilder {
	/**
	 * One time per 24H on MON-FRI
	 */
	@Value("${tiamat.poi.update.cron.schedule:0+*+*/23+?+*+MON-FRI}")
	private String cronSchedule;

	@Value("${osm.poi.blobstore.subdirectory:osm/poi}")
	private String blobStoreSubdirectoryForPointsOfInterest;

	@Override
	public void configure() throws Exception {
		super.configure();
	}
}
