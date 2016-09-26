package no.rutebanken.marduk.routes.file;

import java.io.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipFileUtils {

	public Set<String> listFilesInZip(File file) {
		try (ZipFile zipFile = new ZipFile(file)) {
			return zipFile.stream().filter(ze -> !ze.isDirectory()).map(ze -> ze.getName()).collect(Collectors.toSet());
		} catch (IOException e) {
			return Collections.emptySet();
		}
	}

	public Set<String> listFilesInZip(byte[] data) {
		return listFilesInZip(new ByteArrayInputStream(data));
	}

	public Set<String> listFilesInZip(InputStream inputStream) {
		Set<String> fileNames = new HashSet<>();
		try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
			ZipEntry zipEntry = zipInputStream.getNextEntry();
			while (zipEntry != null) {
				fileNames.add(zipEntry.getName());
				zipEntry = zipInputStream.getNextEntry();
			}
			return fileNames;
		} catch (IOException e) {
			e.printStackTrace();
			return Collections.emptySet();
		}
	}

	public static InputStream addFilesToZip(InputStream source, File[] files) {
		try {
			String name = UUID.randomUUID().toString();
			File tmpZip = File.createTempFile(name, null);
			tmpZip.delete();
			byte[] buffer = new byte[1024 * 32];
			ZipInputStream zin = new ZipInputStream(source);
			ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tmpZip));

			for (int i = 0; i < files.length; i++) {
				InputStream in = new FileInputStream(files[i]);
				out.putNextEntry(new ZipEntry(files[i].getName()));
				for (int read = in.read(buffer); read > -1; read = in.read(buffer)) {
					out.write(buffer, 0, read);
				}
				out.closeEntry();
				in.close();
			}

			for (ZipEntry ze = zin.getNextEntry(); ze != null; ze = zin.getNextEntry()) {
				out.putNextEntry(ze);
				for (int read = zin.read(buffer); read > -1; read = zin.read(buffer)) {
					out.write(buffer, 0, read);
				}
				out.closeEntry();
			}

			out.close();
			return new AutoDeleteOnCloseFileInputStream(tmpZip);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}

}