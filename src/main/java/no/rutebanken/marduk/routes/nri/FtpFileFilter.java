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
import org.apache.camel.component.file.GenericFileFilter;
import org.springframework.stereotype.Component;

@Component(value = "ftpFileFilter")
public class FtpFileFilter<T> implements GenericFileFilter<T> {

	public boolean accept(GenericFile<T> file) {

		String filenameUppercase = file.getFileName().toUpperCase();

		// we only want zip or rar files or directories and not the GTFS folder
		return file.isDirectory()
				|| (!file.isDirectory() && !file.getAbsoluteFilePath().contains("/GTFS/") && (filenameUppercase.endsWith(".ZIP") || filenameUppercase.endsWith(".RAR")));
	}
}
