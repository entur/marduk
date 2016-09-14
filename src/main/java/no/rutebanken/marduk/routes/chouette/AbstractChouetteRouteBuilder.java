package no.rutebanken.marduk.routes.chouette;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.JSON_PART;

import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;

import com.google.common.base.Strings;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.BaseRouteBuilder;

public abstract class AbstractChouetteRouteBuilder extends BaseRouteBuilder{

	protected void toGenericChouetteMultipart(Exchange exchange) {
	    String jsonPart = exchange.getIn().getHeader(JSON_PART, String.class);
	    if (Strings.isNullOrEmpty(jsonPart)) {
	        throw new IllegalArgumentException("No json data");
	    }
	
	    MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
	    entityBuilder.addBinaryBody("parameters", exchange.getIn().getHeader(JSON_PART, String.class).getBytes(), ContentType.DEFAULT_BINARY, "parameters.json");
	
	    exchange.getOut().setBody(entityBuilder.build());
	    exchange.getOut().setHeaders(exchange.getIn().getHeaders());
	    exchange.getOut().setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"));
	}

	protected void toImportMultipart(Exchange exchange) {
	    String fileName = exchange.getIn().getHeader(FILE_HANDLE, String.class);
	    if (Strings.isNullOrEmpty(fileName)) {
	        throw new IllegalArgumentException("No file handle");
	    }
	
	    String jsonPart = exchange.getIn().getHeader(JSON_PART, String.class);
	    if (Strings.isNullOrEmpty(jsonPart)) {
	        throw new IllegalArgumentException("No json data");
	    }
	
	    InputStream inputStream = exchange.getIn().getBody(InputStream.class);
	    if (inputStream == null) {
	        throw new IllegalArgumentException("No data");
	    }
	
	    MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
	    entityBuilder.addBinaryBody("parameters", exchange.getIn().getHeader(JSON_PART, String.class).getBytes(), ContentType.DEFAULT_BINARY, "parameters.json");
	    entityBuilder.addBinaryBody("feed", inputStream, ContentType.DEFAULT_BINARY, fileName);
	
	    exchange.getOut().setBody(entityBuilder.build());
	    exchange.getOut().setHeaders(exchange.getIn().getHeaders());
	    exchange.getOut().setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"));
	}
	
	
	public boolean shouldTransferData(Exchange exchange) {
		Provider currentProvider = getProviderRepository().getProvider(exchange.getIn().getHeader(Constants.PROVIDER_ID,Long.class));
		return currentProvider.chouetteInfo.migrateDataToProvider != null;
	}

}
