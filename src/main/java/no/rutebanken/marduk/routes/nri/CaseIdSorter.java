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

package no.rutebanken.marduk.routes.nri;

import org.apache.camel.component.file.GenericFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component(value = "caseIdNriFtpSorter")
public class CaseIdSorter<T> implements Comparator<GenericFile<T>> {

	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@Override
	public int compare(GenericFile<T> o1, GenericFile<T> o2) {

		logger.trace("Comparing '" + o1.getRelativeFilePath() + "' with '" + o2.getRelativeFilePath() + "'");

		Pattern p = Pattern.compile(".*/([0-9]{3,4})/.*");
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
