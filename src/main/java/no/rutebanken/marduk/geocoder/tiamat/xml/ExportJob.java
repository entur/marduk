package no.rutebanken.marduk.geocoder.tiamat.xml;


import com.google.common.base.MoreObjects;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.datatype.XMLGregorianCalendar;

@XmlRootElement
public class ExportJob {

	private Long id;

	private String jobUrl;
	private String fileName;

	private String message;

	private XMLGregorianCalendar started;
	private XMLGregorianCalendar finished;

	private JobStatus status;

	public ExportJob() {
	}

	public ExportJob(JobStatus jobStatus) {
		status = jobStatus;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				       .omitNullValues()
				       .add("id", id)
				       .add("status", status)
				       .add("jobUrl", jobUrl)
				       .add("fileName", fileName)
				       .add("started", started)
				       .add("finished", finished)
				       .add("message", message)
				       .toString();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getJobUrl() {
		return jobUrl;
	}

	public void setJobUrl(String jobUrl) {
		this.jobUrl = jobUrl;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public XMLGregorianCalendar getStarted() {
		return started;
	}

	public void setStarted(XMLGregorianCalendar started) {
		this.started = started;
	}

	public XMLGregorianCalendar getFinished() {
		return finished;
	}

	public void setFinished(XMLGregorianCalendar finished) {
		this.finished = finished;
	}

	public JobStatus getStatus() {
		return status;
	}

	public void setStatus(JobStatus status) {
		this.status = status;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}