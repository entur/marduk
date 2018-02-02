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

package no.rutebanken.marduk.geocoder.routes.control;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeoCoderTaskMessage {

	private SortedSet<GeoCoderTask> tasks;

	public GeoCoderTaskMessage() {
		tasks = new TreeSet<>();
	}

	public GeoCoderTaskMessage(Collection<GeoCoderTask> tasks) {
		this();
		if (tasks != null) {
			this.tasks.addAll(tasks);
		}
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
