package org.integratedmodelling.common.distribution;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.integratedmodelling.klab.api.exceptions.KlabException;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.scope.Scope;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.URL;
import java.util.function.BiConsumer;

public class Downloader {
	private long totalLength;
	private BiConsumer<Long, Long> handler;
	private URL url;
	private File file;
	private int retries;
	private int maxRetries;
	private static final int MAX_RETRIES = 5;
	/**
	 * MD5 checksum
	 */
	private String checksum;
	private Scope scope;

	public Downloader(URL url, File file, BiConsumer<Long, Long> handler) {
		this(url, file, handler, null, MAX_RETRIES);
	}
	
	public Downloader(URL url, File file, BiConsumer<Long, Long> handler, String checksum) {
        this(url, file, handler, checksum, MAX_RETRIES);
    }
	
	public Downloader(URL url, File file, BiConsumer<Long, Long> handler, String checksum, int maxRetries) {
	    this.url = url;
        this.file = file;
        this.handler = handler;
        this.checksum = checksum;
        this.maxRetries = maxRetries;
        this.retries = 0;
	}
	
	private class ProgressListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			handler.accept(((DownloadCountingOutputStream) e.getSource()).getByteCount(), totalLength);
		}
	}

	/**
	 * Start a download thread and return.
	 */
	public void startDownload() {
		new Thread() {
            @Override
            public void run() {
                download();
            }
		}.start();
	}
	
	/**
	 * Start downloading and block until success or failure.
	 */
	public void download() {
		ProgressListener progressListener = new ProgressListener();
		try (OutputStream os = new FileOutputStream(file); InputStream is = url.openStream()) {
		    DownloadCountingOutputStream dcount = new DownloadCountingOutputStream(os);
			dcount.setListener(progressListener);
			String contentLength = url.openConnection().getHeaderField("Content-Length");
			if (contentLength == null) {
			    dcount.close();
			    throw new KlabIOException("Content lenght is null");
			}
			this.totalLength = Integer.parseInt(contentLength);
			IOUtils.copy(is, dcount);
			if (checksum != null) {
			    String md5 = DigestUtils.md5Hex(new FileInputStream(file));
			    if (!md5.equals(checksum)) {
			         throw new KlabIOException("Invalid checksum for file ["+file+"], retry " + (this.retries + 1));
			    }
			}
			finish();
		} catch (Exception e) {
			fail(e);
		}
	}
	
	protected void finish() {
	    this.retries = 0;
	}

	protected void fail(Exception e)  {
	    if (this.retries < this.maxRetries) {
	        scope.error("Retry: " + e);
	        this.retries++;
	        download();
	    } else {
	        this.retries = 0;
	        scope.error(e.getMessage());
	        throw new KlabIOException(e);
	    }  
	}

	class DownloadCountingOutputStream extends CountingOutputStream {

		private ActionListener listener = null;

		public DownloadCountingOutputStream(OutputStream out) {
			super(out);
		}

		public void setListener(ActionListener listener) {
			this.listener = listener;
		}

		@Override
		protected void afterWrite(int n) throws IOException {
			super.afterWrite(n);
			if (listener != null) {
				listener.actionPerformed(new ActionEvent(this, 0, null));
			}
		}
	}
	/*
	public static void main(String[] args) throws Exception {
		URL url = new URL("http://www.integratedmodelling.org/downloads/IALE15_ehabitat_JML.pdf");
		File file = new File(System.getProperty("user.home") + File.separator + "dio.pdf");
		Downloader downloader = new Downloader(url, file, (sofar, total) -> System.out.println("Downloaded " + sofar + "/" + total));
		downloader.download();
	}
    */
}
