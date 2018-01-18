package no.rutebanken.marduk.routes.mapbox.model;

public class MapboxUploadRequest {

    private String tileset;
    private String url;
    private String name;

    public MapboxUploadRequest(String tileset, String url, String name) {
        this.tileset = tileset;
        this.url = url;
        this.name = name;
    }

    public String getTileset() {
        return tileset;
    }

    public void setTileset(String tileset) {
        this.tileset = tileset;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
