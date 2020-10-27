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

package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.component.http.HttpMethods;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.springframework.util.StringUtils;

import java.io.InputStream;

import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.JSON_PART;

public abstract class AbstractChouetteRouteBuilder extends BaseRouteBuilder{

	protected void toGenericChouetteMultipart(Exchange exchange) {
	    String jsonPart = exchange.getIn().getHeader(JSON_PART, String.class);
	    if (StringUtils.isEmpty(jsonPart)) {
	        throw new IllegalArgumentException("No json data");
	    }

	    MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
	    entityBuilder.addBinaryBody("parameters", exchange.getIn().getHeader(JSON_PART,byte[].class), ContentType.DEFAULT_BINARY, "parameters.json");

	    exchange.getMessage().setBody(entityBuilder.build());
	    exchange.getMessage().setHeaders(exchange.getIn().getHeaders());
	    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"));
		exchange.getMessage().setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST));
	}

	protected void toImportMultipart(Exchange exchange) {
	    String fileName = exchange.getIn().getHeader(FILE_NAME, String.class);
	    if (StringUtils.isEmpty(fileName)) {
	        throw new IllegalArgumentException("No file handle");
	    }

	    String jsonPart = exchange.getIn().getHeader(JSON_PART, String.class);
	    if (StringUtils.isEmpty(jsonPart)) {
	        throw new IllegalArgumentException("No json data");
	    }

	    InputStream inputStream = exchange.getIn().getBody(InputStream.class);
	    if (inputStream == null) {
	        throw new IllegalArgumentException("No data");
	    }

	    MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
	    entityBuilder.addBinaryBody("parameters", exchange.getIn().getHeader(JSON_PART, byte[].class), ContentType.DEFAULT_BINARY, "parameters.json");
	    entityBuilder.addBinaryBody("feed", inputStream, ContentType.DEFAULT_BINARY, fileName);

	    exchange.getMessage().setBody(entityBuilder.build());
	    exchange.getMessage().setHeaders(exchange.getIn().getHeaders());
	    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"));
	}


	public boolean shouldTransferData(Exchange exchange) {
		Provider currentProvider = getProviderRepository().getProvider(exchange.getIn().getHeader(Constants.PROVIDER_ID,Long.class));
		return currentProvider.chouetteInfo.migrateDataToProvider != null;
	}

}
