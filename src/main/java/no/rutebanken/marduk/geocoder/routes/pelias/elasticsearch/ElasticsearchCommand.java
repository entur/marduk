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

package no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;

import java.io.IOException;
import java.io.StringWriter;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ElasticsearchCommand {

    @JsonProperty("index")
    private ActionMetaData index;
    @JsonProperty("delete")
    private ActionMetaData delete;
    @JsonProperty("create")
    private ActionMetaData create;
    @JsonProperty("update")
    private ActionMetaData update;

    @JsonIgnore
    private Object source;

    public ActionMetaData getIndex() {
        return index;
    }

    public void setIndex(ActionMetaData index) {
        this.index = index;
    }

    public ActionMetaData getDelete() {
        return delete;
    }

    public void setDelete(ActionMetaData delete) {
        this.delete = delete;
    }

    public ActionMetaData getCreate() {
        return create;
    }

    public void setCreate(ActionMetaData create) {
        this.create = create;
    }

    public ActionMetaData getUpdate() {
        return update;
    }

    public void setUpdate(ActionMetaData update) {
        this.update = update;
    }

    public Object getSource() {
        return source;
    }

    public void setSource(Object source) {
        this.source = source;
    }

    public static ElasticsearchCommand peliasIndexCommand(PeliasDocument document) {
        if (document == null) {
            return null;
        }
        ElasticsearchCommand command = new ElasticsearchCommand();
        command.setIndex(new ActionMetaData("pelias", document.getLayer(), document.getSourceId()));
        command.setSource(document);
        return command;

    }

    public String toString() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, this);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
