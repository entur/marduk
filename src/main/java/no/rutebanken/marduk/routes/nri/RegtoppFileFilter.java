package no.rutebanken.marduk.routes.nri;

import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileFilter;
import org.springframework.stereotype.Component;

@Component(value = "regtoppFileFilter")
public class RegtoppFileFilter<T> implements GenericFileFilter<T> {

    public boolean accept(GenericFile<T> file) {
        
    	// we only want zip files or directories 
    	return file.isDirectory() || (!file.isDirectory() && file.getFileName().endsWith(".zip"));
    }
}
