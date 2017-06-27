package no.rutebanken.marduk.geocoder.routes.tiamat.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TiamatExportTasks {

    private List<TiamatExportTask> tasks = new ArrayList<>();

    private TiamatExportTask currentTask;

    private TiamatExportTasks() {
    }

    public TiamatExportTasks(Collection<TiamatExportTask> tasks) {

        if (!CollectionUtils.isEmpty(tasks)) {
            this.tasks.addAll(tasks);
            popNextTask();
        }
    }

    public TiamatExportTasks(TiamatExportTask... taskArray) {
        this(Arrays.asList(taskArray));
    }

    public List<TiamatExportTask> getTasks() {
        return tasks;
    }

    public void setTasks(List<TiamatExportTask> tasks) {
        this.tasks = tasks;
    }

    public void setCurrentTask(TiamatExportTask currentTask) {
        this.currentTask = currentTask;
    }

    public TiamatExportTask getCurrentTask() {
        return currentTask;
    }

    @JsonIgnore
    public int getSize() {
        return tasks.size();
    }

    @JsonIgnore
    public boolean isComplete() {
        return currentTask == null;
    }

    public void addTask(TiamatExportTask task) {
        tasks.add(task);
    }

    public TiamatExportTask popNextTask() {

        if (tasks.isEmpty()) {
            currentTask = null;
        } else {
            currentTask = tasks.remove(0);
        }
        return currentTask;
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

    public static TiamatExportTasks fromString(String string) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(string, TiamatExportTasks.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
