package no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;

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

		ElasticsearchCommand command = new ElasticsearchCommand();
		String id = document.getSource() + ":" + document.getSourceId();
		command.setIndex(new ActionMetaData("pelias", document.getLayer(), id));
		command.setSource(document);
		return command;

	}
}
