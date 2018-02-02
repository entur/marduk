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

package no.rutebanken.marduk.routes.file;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.impl.FileVolumeManager;
import com.github.junrar.rarfile.FileHeader;

import no.rutebanken.marduk.routes.file.beans.FileTypeClassifierBean;

public class RARToZipFilesSplitter {

	private static Logger logger = LoggerFactory.getLogger(RARToZipFilesSplitter.class);

	public static List<Object> splitRarFile(@Body Object b, Exchange exchange) throws IOException, RarException {

		logger.info("Splitting rar file");

		List<Object> zipFileObjects = new ArrayList<Object>();

		// Create tmp file on disk with content
		File tmpFolder = new File(System.getProperty("java.io.tmpdir"));
		File rarExtractFolder = new File(tmpFolder, UUID.randomUUID().toString());
		rarExtractFolder.mkdirs();

		File rarFile = new File(tmpFolder, UUID.randomUUID().toString());

		try {
			rarFile.createNewFile();
			FileOutputStream fos = new FileOutputStream(rarFile);

			if (b instanceof byte[]) {
				fos.write((byte[]) b);
				fos.close();
			} else if (b instanceof InputStream) {
				IOUtils.copyLarge((InputStream) b, fos);
				fos.close();
			} else {
				fos.close();
				throw new RuntimeException("Cannot handle body of type " + b);
			}

			logger.info("Processing RAR file with length " + rarFile.length());

			// Unpack to new folder
			Archive a = new Archive(new FileVolumeManager(rarFile));
			FileHeader fh = a.nextFileHeader();
			while (fh != null) {
				File out = new File(rarExtractFolder, fh.getFileNameString().trim().replace('\\', '/'));
				File parentFolder = out.getParentFile();
				parentFolder.mkdirs();

				if (!out.isDirectory()) {
					FileOutputStream os = new FileOutputStream(out);
					a.extractFile(fh, os);
					os.close();
				}
				fh = a.nextFileHeader();
			}
			a.close();

			// Iterate content in folder
			zipFileObjects.addAll(processFolder(rarExtractFolder, exchange));
		} catch (Exception e) {
			logger.error("Error extracting RAR file", e);
		} finally {
			// FileUtil.deleteFile(rarFile);
			// FileUtil.removeDir(rarExtractFolder);
		}

		logger.info("Done splitting RAR file, results are " + ToStringBuilder.reflectionToString(zipFileObjects));

		return zipFileObjects;
	}

	public static List<Object> processFolder(File folder, Exchange exchange) throws IOException, RarException {
		logger.info("Scanning directory " + folder.getAbsolutePath());
		List<Object> zipFileObjects = new ArrayList<Object>();

		boolean regtoppZip = FileTypeClassifierBean.isRegtoppZip(new HashSet<String>(Arrays.asList(folder.list())));
		if (regtoppZip) {
			logger.info("Directory " + folder.getAbsolutePath() + " is a Regtopp folder");

			// Zip files together
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			ZipOutputStream zos = new ZipOutputStream(os);
			for (File f : folder.listFiles()) {
				ZipEntry entry = new ZipEntry(f.getName());
				zos.putNextEntry(entry);

				FileInputStream fis = new FileInputStream(f);
				IOUtils.copyLarge(fis, zos);
				fis.close();
			}
			zos.close();
			zipFileObjects.add(os.toByteArray());
			logger.info("Zipped files in directory " + folder.getAbsolutePath());
		} else {
			logger.info("Not a Regtopp directory " + folder.getAbsolutePath() + ", scanning content");

			for (File f : folder.listFiles()) {
				logger.info("Checking file " + f.getAbsolutePath());

				if (f.isFile() && f.getName().toUpperCase().endsWith(".ZIP")) {
					// Already zipped here
					FileInputStream fis = new FileInputStream(f);
					byte[] byteArray = IOUtils.toByteArray(fis);
					fis.close();
					zipFileObjects.add(byteArray);
					logger.info("Added existing zip file " + f.getAbsolutePath());

				} else if (f.isFile() && f.getName().toUpperCase().endsWith(".RAR")) {
					// Embedded rar
					FileInputStream fis = new FileInputStream(f);
					byte[] byteArray = IOUtils.toByteArray(fis);
					fis.close();

					zipFileObjects.add(splitRarFile(byteArray, exchange));
					logger.info("Added result of expanding embeddced rar file " + f.getAbsolutePath());

				} else if (f.isDirectory()) {
					// Recurse
					logger.info("Recursing into directory " + f.getAbsolutePath());
					zipFileObjects.addAll(processFolder(f, exchange));
				}
			}
		}
		return zipFileObjects;
	}

}
