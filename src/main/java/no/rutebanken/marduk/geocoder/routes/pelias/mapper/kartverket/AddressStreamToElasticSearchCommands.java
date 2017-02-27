package no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket;

import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.kartverket.KartverketAddress;
import no.rutebanken.marduk.geocoder.routes.pelias.kartverket.KartverketAddressReader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collection;
import java.util.stream.Collectors;

@Service
public class AddressStreamToElasticSearchCommands {

	private AddressToPeliasMapper mapper = new AddressToPeliasMapper();

	public Collection<ElasticsearchCommand> transform(InputStream addressStream) {
		return new KartverketAddressReader().read(addressStream).stream().map(a -> toCommand(a)).collect(Collectors.toList());
	}


	private ElasticsearchCommand toCommand(KartverketAddress address) {
		return ElasticsearchCommand.peliasIndexCommand(mapper.toPeliasDocument(address));
	}


}
