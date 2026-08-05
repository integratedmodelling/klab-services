package org.integratedmodelling.common.distribution;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.URL;
import java.util.function.BiConsumer;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.scope.Scope;

public class Downloader {

  private long totalLength;
  private BiConsumer<Long, Long> handler;
  private URL url;
  private File file;
  private int retries;
  private int maxRetries;
  private static final int MAX_RETRIES = 5;

  /** MD5 checksum */
  private String checksum;

  private Scope scope;

  public Downloader(URL url, File file, BiConsumer<Long, Long> handler) {
    this(url, file, handler, null, MAX_RETRIES);
  }

  public Downloader(URL url, File file, BiConsumer<Long, Long> handler, String checksum) {
    this(url, file, handler, checksum, MAX_RETRIES);
  }

  public Downloader(
      URL url, File file, BiConsumer<Long, Long> handler, String checksum, int maxRetries) {
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

  /** Start a download thread and return. */
  public void startDownload() {
    Thread.ofVirtual().start(this::download);
  }

  /** Start downloading and block until success or failure. */
  public boolean download() {
    Exception lastFailure = null;
    for (this.retries = 0; this.retries <= this.maxRetries; this.retries++) {
      try {
        downloadOnce();
        finish();
        return true;
      } catch (Exception e) {
        lastFailure = e;
        if (file.exists()) {
          file.delete();
        }
        if (scope != null && this.retries < this.maxRetries) {
          scope.warn("Retrying download after: " + e.getMessage());
        }
      }
    }
    this.retries = 0;
    if (scope != null && lastFailure != null) {
      scope.error(lastFailure.getMessage());
    }
    if (lastFailure == null) {
      throw new KlabIOException("Download failed");
    }
    throw new KlabIOException(lastFailure);
  }

  private void downloadOnce() throws IOException {
    var parent = file.getParentFile();
    if (parent != null) {
      parent.mkdirs();
    }
    var connection = url.openConnection();
    this.totalLength = connection.getContentLengthLong();
    try (InputStream is = connection.getInputStream();
        OutputStream os = new FileOutputStream(file);
        DownloadCountingOutputStream dcount = new DownloadCountingOutputStream(os)) {
      if (handler != null) {
        dcount.setListener(new ProgressListener());
      }
      IOUtils.copy(is, dcount);
    }
    if (checksum != null) {
      try (var input = new FileInputStream(file)) {
        String md5 = DigestUtils.md5Hex(input);
        if (!md5.equalsIgnoreCase(checksum)) {
          throw new KlabIOException("Invalid checksum for file [" + file + "]");
        }
      }
    }
  }

  protected void finish() {
    this.retries = 0;
  }

  protected void fail(Exception e) {
    if (this.retries < this.maxRetries) {
      if (scope != null) {
        scope.error("Retry: " + e);
      }
      this.retries++;
      download();
    } else {
      this.retries = 0;
      if (scope != null) {
        scope.error(e.getMessage());
      }
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

  public static void main(String[] args) throws Exception {
    URL url = new URL("https://download.fshub.io/releases/lrm-setup-5.9.4.zip");
    File file = new File(System.getProperty("user.home") + File.separator + "dio.zip");
    Downloader downloader =
        new Downloader(
            url,
            file,
            (sofar, total) -> System.out.println("Downloaded " + sofar + "/" + total + "\r"));
    downloader.download();
  }
}
