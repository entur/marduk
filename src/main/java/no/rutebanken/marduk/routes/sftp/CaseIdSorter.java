package no.rutebanken.marduk.routes.sftp;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.component.file.GenericFile;
import org.springframework.stereotype.Component;

@Component(value = "caseIdSftpSorter")
public class CaseIdSorter<T> implements Comparator<GenericFile<T>> {
	
	@Override
	public int compare(GenericFile<T> o1, GenericFile<T> o2) {
		
		Pattern p = Pattern.compile(".*_([0-9]{3,4})_.*");
		Matcher m1 = p.matcher(o1.getRelativeFilePath());
		Matcher m2 = p.matcher(o2.getRelativeFilePath());
		
		if(m1.matches() && m2.matches()) {
			String g1 = m1.group(1);
			String g2 = m2.group(1);
			
			return Integer.parseInt(g1) - Integer.parseInt(g2);
		} else {
			return o1.getRelativeFilePath().compareTo(o2.getRelativeFilePath());
		}
	}

	

}
