package no.rutebanken.marduk.routes.mapbox.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MapBoxAwsCredentials {

    @JsonProperty("bucket")
    private String bucket;
    @JsonProperty("key")
    private String key;
    @JsonProperty("accessKeyId")
    private String accessKeyId;
    @JsonProperty("secretAccessKey")
    private String secretAccessKey;
    @JsonProperty("sessionToken")
    private String sessionToken;
    @JsonProperty("url")
    private String url;

    @JsonProperty("bucket")
    public String getBucket() {
        return bucket;
    }

    @JsonProperty("bucket")
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    @JsonProperty("key")
    public String getKey() {
        return key;
    }

    @JsonProperty("key")
    public void setKey(String key) {
        this.key = key;
    }

    @JsonProperty("accessKeyId")
    public String getAccessKeyId() {
        return accessKeyId;
    }

    @JsonProperty("accessKeyId")
    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    @JsonProperty("secretAccessKey")
    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    @JsonProperty("secretAccessKey")
    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    @JsonProperty("sessionToken")
    public String getSessionToken() {
        return sessionToken;
    }

    @JsonProperty("sessionToken")
    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    @JsonProperty("url")
    public String getUrl() {
        return url;
    }

    @JsonProperty("url")
    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("bucket", bucket)
                .add("key", key)
                .add("accessKeyId", accessKeyId)
                .add("secretAccessKey", secretAccessKey)
                .add("sessionToken", sessionToken)
                .add("url", url)
                .toString();
    }
}