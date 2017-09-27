package no.rutebanken.marduk.routes.etcd.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EtcdResponse {

    public String action;

    public String errorCode;

    public String message;

    public EtcdNode node;

}
