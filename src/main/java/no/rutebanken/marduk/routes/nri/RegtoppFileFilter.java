package no.rutebanken.marduk.routes.nri;

import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileFilter;
import org.springframework.stereotype.Component;

@Component(value = "regtoppFileFilter")
public class RegtoppFileFilter<T> implements GenericFileFilter<T> {

	public boolean accept(GenericFile<T> file) {

		String filenameUppercase = file.getFileName().toUpperCase();

		// we only want zip or rar files or directories
		return file.isDirectory()
				|| (!file.isDirectory() && (filenameUppercase.endsWith(".ZIP") || filenameUppercase.endsWith(".RAR")));
	}
}
