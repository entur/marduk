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
