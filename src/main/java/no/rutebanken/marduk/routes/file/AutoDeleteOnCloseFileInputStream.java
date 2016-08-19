package no.rutebanken.marduk.routes.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link FileInputStream} which (optionally) deletes the underlying file when
 * the stream is closed.
 *
 * @author BJ
 */
public class AutoDeleteOnCloseFileInputStream extends FileInputStream {

	private static final Logger log = Logger.getAnonymousLogger();

	/**
	 * Underlying file object
	 */
	private File fileObj;

	/**
	 * Flag to control auto-delete of file on close()
	 */
	private final boolean deleteOnClose;

	/**
	 * Is underlying file stream closed. Becomes true after close() is invoked.
	 */
	private boolean isClosed;

	/**
	 * Was underlying File object deleted.
	 */
	private boolean isDeleted;

	/**
	 * Creates a fileInputStream wrapped around the given file and deletes the
	 * file when the FileInputStream is closed.
	 *
	 * @param file
	 *
	 * @throws FileNotFoundException
	 */
	public AutoDeleteOnCloseFileInputStream(File file) throws FileNotFoundException {
		this(file, true);
	}

	/**
	 * Creates a fileInputStream wrapped around the given file.
	 *
	 * @param file
	 * @param deleteOnClose
	 *
	 * @throws FileNotFoundException
	 */
	public AutoDeleteOnCloseFileInputStream(final File file, final boolean deleteOnClose) throws FileNotFoundException {
		super(file);
		this.fileObj = file;
		this.deleteOnClose = deleteOnClose;
		isClosed = false;
		isDeleted = false;
	}

	/**
	 * @return boolean flag, true if the file should be deleted on close().
	 *         Default is true.
	 */
	public final boolean isDeleteOnClose() {
		return deleteOnClose;
	}

	/**
	 * @return is file deleted (after close)
	 */
	public boolean isDeleted() {
		return isDeleted;
	}

	/**
	 * Closes the underlying FileInputStream and also deletes the file object
	 * from disk if, the isDeleteOnClose() is set to true.
	 *
	 * @see java.io.FileInputStream#close()
	 */
	@Override
	public void close() {

		if (isClosed) {
			log.finer("close()- already closed: " + this);
			return; // no-op
		}

		log.fine("close()- closing: " + this);
		isClosed = true;

		try {
			super.close();
			if (isDeleteOnClose()) {
				isDeleted = fileObj.delete();
			}
		} catch (IOException e) {
			log.log(Level.WARNING, "Failed to close() stream: " + this, e);
		} catch (RuntimeException e) {
			log.log(Level.WARNING, "Failed to delete(): " + fileObj, e);
		}

		log.info("close()- file [" + fileObj + "] deleted: " + isDeleted);
		fileObj = null;
	}

	@Override
	public String toString() {

	    String defaultStr = super.toString();

	    try {
	        StringBuilder sb = new StringBuilder();
	        sb.append(defaultStr)
	        .append("{")
	        .append("File=").append(fileObj).append(", ")
	        .append("File size=").append(fileObj == null ? -1L :  fileObj.length()).append(", ")
	        .append("deleteOnClose=").append(deleteOnClose).append(", ")
	        .append("isClosed=").append(isClosed).append(", ")
	        .append("isDeleted=").append(isDeleted)
	        .append("}");
	        return sb.toString();
	    }
	    catch(RuntimeException e){
	        log.log(Level.INFO, "Failed to stringify", e);
	        return defaultStr;
	    }
	}
}
