package no.rutebanken.marduk.geocoder.routes.tiamat.model;

import no.rutebanken.marduk.exceptions.MardukException;

public class TiamatExportTask {

    public static final String SEPARATOR = ",";

    public String name;

    public String queryString = "";

    private TiamatExportTask() {
    }

    public TiamatExportTask(String name, String queryString) {
        this.name = name;
        this.queryString = queryString;
    }

    public TiamatExportTask(String config) {
        if (config == null || config.split(SEPARATOR).length < 1 || config.split(SEPARATOR).length > 2) {
            throw new MardukException("Invalid config string, should contain 'name' and optionally 'queryString' separated by '" + SEPARATOR + "' : " + config);
        }

        String[] configArray = config.split(SEPARATOR);
        name = configArray[0].trim();
        if (configArray.length > 1) {
            queryString = configArray[1].trim();
        }
    }

    public String getName() {
        return name;
    }

    public String getQueryString() {
        return queryString;
    }

    @Override
    public String toString() {
        return "TiamatExportConfig{" +
                       "name='" + name + '\'' +
                       ", queryString='" + queryString + '\'' +
                       '}';
    }
}
