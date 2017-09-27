package no.rutebanken.marduk.geocoder.routes.tiamat.model;

import no.rutebanken.marduk.exceptions.MardukException;

public class TiamatExportTask {

    public static final String SEPARATOR = ",";

    public String name;

    public String queryString = "";

    public TiamatExportTaskType type = TiamatExportTaskType.ASYNC;

    private TiamatExportTask() {
    }

    public TiamatExportTask(String name, String queryString) {
        this.name = name;
        this.queryString = queryString;
    }

    public TiamatExportTask(String name, String queryString, TiamatExportTaskType type) {
        this.name = name;
        this.queryString = queryString;
        this.type = type;
    }

    public TiamatExportTask(String config) {
        if (config == null || config.split(SEPARATOR).length < 1 || config.split(SEPARATOR).length > 3) {
            throw new MardukException("Invalid config string, should contain 'name' and optionally 'queryString' separated by '" + SEPARATOR + "' : " + config);
        }

        String[] configArray = config.split(SEPARATOR);
        name = configArray[0].trim();
        if (configArray.length > 1) {
            queryString = configArray[1].trim();
        }
        if (configArray.length > 2) {
            type = TiamatExportTaskType.valueOf(configArray[2].trim());
        }
    }

    public String getName() {
        return name;
    }

    public String getQueryString() {
        return queryString;
    }

    public TiamatExportTaskType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "TiamatExportConfig{" +
                       "name='" + name + '\'' +
                       ", queryString='" + queryString + '\'' +
                       '}';
    }
}
