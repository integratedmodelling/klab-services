package org.integratedmodelling.klab.runtime.language;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.data.StorageScan;

/** Invocation-owned leases, transferred to a streaming result until close, EOF or read failure. */
public final class ScanResources implements AutoCloseable {
  private final List<StorageScan.Session<?>> sessions = new ArrayList<>();
  private boolean transferred;
  private boolean closed;
  public <T extends StorageScan.Session<?>> T add(T session) {
    if (closed || transferred) { session.close(); throw new IllegalStateException("Invocation already completed"); }
    sessions.add(session); return session;
  }
  public InputStream transfer(InputStream stream) {
    if (closed || transferred) throw new IllegalStateException("Invocation already completed");
    transferred = true;
    return new FilterInputStream(stream) {
      private boolean finished;
      @Override public int read() throws IOException {
        try { int result = super.read(); if (result < 0) close(); return result; }
        catch (IOException | RuntimeException e) { fail(e); throw e; }
      }
      @Override public int read(byte[] b, int off, int len) throws IOException {
        try { int result = in.read(b, off, len); if (result < 0) close(); return result; }
        catch (IOException | RuntimeException e) { fail(e); throw e; }
      }
      private void fail(Exception failure) {
        try { close(); } catch (Exception e) { failure.addSuppressed(e); }
      }
      @Override public void close() throws IOException {
        if (finished) return;
        finished = true;
        try { super.close(); } finally { release(); }
      }
    };
  }
  private synchronized void release() {
    if (closed) return;
    closed = true;
    RuntimeException failure = null;
    for (int i = sessions.size() - 1; i >= 0; i--) {
      try { sessions.get(i).close(); }
      catch (RuntimeException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
    }
    if (failure != null) throw failure;
  }
  @Override public void close() { if (!transferred) release(); }
}
