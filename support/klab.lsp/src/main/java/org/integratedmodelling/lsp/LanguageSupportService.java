package org.integratedmodelling.lsp;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.*;

/**
 * Additional server providing (cached) data for the language highlighters and resource validators,
 * mostly for the use of the editor. Meant to run locally wherever LSP is needed, to provide
 * semantically-aware highlighting and validation.
 *
 * <p>- `/type/<concept>` → the reference semantic type for a single namespace:DioCan -
 * `/resource/<urn>` → the status of a URN (online, offline, error) - `/keywords/<language>` → the
 * list of keywords for the passed language - `/opdate/<urn>` repost the info about a resource or a
 * namespace
 *
 * <p>TODO just a stub for now.
 */
public class LanguageSupportService {

  private HttpServer server;
  private boolean serviceStopped = true;
  private Gson gson = new Gson();

  public void start(int port) throws Exception {

    this.server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/test", new MyHandler()); // TODO create endpoints
    server.setExecutor(null); // creates a default executor
    // TODO check and load the configuration files; start watch service
    serviceStopped = false;
    server.start();
  }

  public void stop() {
    serviceStopped = true;
    server.stop(0);
  }

  static class MyHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange t) throws IOException {
      String response = "This is the response";
      t.sendResponseHeaders(200, response.length());
      OutputStream os = t.getResponseBody();
      os.write(response.getBytes());
      os.close();
    }
  }

  /**
   * Blocks until stopped - run in a thread!
   *
   * @param path
   * @return
   */
  private boolean monitorConfiguration(Path path) {
    try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
      // Register for creation, modification, and deletion events
      path.register(
          watchService,
          StandardWatchEventKinds.ENTRY_CREATE,
          StandardWatchEventKinds.ENTRY_MODIFY,
          StandardWatchEventKinds.ENTRY_DELETE);

      while (!serviceStopped) {
        WatchKey key = watchService.take(); // Blocks until an event occurs
        for (WatchEvent<?> event : key.pollEvents()) {
          WatchEvent.Kind<?> kind = event.kind();
          if (kind == StandardWatchEventKinds.OVERFLOW) continue;

          Path fileName = (Path) event.context();
          // TODO if file is a configuration file, update the cache (synchronize)
          System.out.println(kind + " - " + fileName);
        }
        key.reset();
      }
    } catch (Exception e) {
      return false;
    }
    return true;
  }
}
