package no.rutebanken.marduk.geocoder.routes.control;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeoCoderTaskMessage {

	private SortedSet<GeoCoderTask> tasks;

	public GeoCoderTaskMessage() {
		tasks = new TreeSet<>();
	}

	public GeoCoderTaskMessage(GeoCoderTask... taskArray) {
		this();
		if (taskArray != null) {
			tasks.addAll(Arrays.asList(taskArray));
		}
	}

	public SortedSet<GeoCoderTask> getTasks() {
		return tasks;
	}

	public void setTasks(SortedSet<GeoCoderTask> tasks) {
		this.tasks = tasks;
	}

	@JsonIgnore
	public boolean isComplete() {
		return tasks.stream().allMatch(t -> t.isComplete());
	}

	public void addTask(GeoCoderTask task) {
		getTasks().add(task);
	}

	public GeoCoderTask popNextTask() {
		GeoCoderTask task = tasks.first();
		tasks.remove(task);
		return task;
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

	public static GeoCoderTaskMessage fromString(String string) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			return mapper.readValue(string, GeoCoderTaskMessage.class);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
