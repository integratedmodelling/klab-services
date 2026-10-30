package org.integratedmodelling.klab.api.services.logging;

/** Administrative diagnostic query. No filesystem paths or caller-selected sources. */
public record LogQuery(String streamId, String cursor, Integer limit, boolean follow,
    String from, String to, String minLevel, String loggerPrefix) {
  public static LogQuery firstPage() {
    return new LogQuery("service", null, 200, false, null, null, null, null);
  }
}
