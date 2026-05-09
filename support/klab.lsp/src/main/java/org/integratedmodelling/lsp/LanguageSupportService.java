package org.integratedmodelling.lsp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * Additional server providing (cached) data for the language highlighters and resource validators,
 * mostly for the use of the editor. Meant to run locally wherever LSP is needed, to provide
 * semantically-aware highlighting and validation.
 *
 * - `/type/<concept>` → the reference semantic type for a single namespace:DioCan
 * - `/resource/<urn>` → the status of a URN (online, offline, error)
 * - `/keywords/<language>` → the list of keywords for the passed language
 * - `/opdate/<urn>` repost the info about a resource or a namespace
 *
 * <p>TODO just a stub for now.
 */
public class LanguageSupportService {

  private HttpServer server;

  public void start(int port) throws Exception {
    this.server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/test", new MyHandler());
    server.setExecutor(null); // creates a default executor
    server.start();
  }

  public void stop() {
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
}
