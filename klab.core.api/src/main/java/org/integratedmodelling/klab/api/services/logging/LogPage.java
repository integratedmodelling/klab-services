package org.integratedmodelling.klab.api.services.logging;

import java.util.List;

/** Continuation is bounded to an instance/actor/source; follow explicitly advances its snapshot. */
public record LogPage(String instanceId, String sourceZone, List<LogRecord> records,
    String nextCursor, boolean caughtUp, long snapshotEnd, int omittedEvents,
    String redactionPolicy, String serviceId) {
  public LogPage(String instanceId,String sourceZone,List<LogRecord> records,String nextCursor,
      boolean caughtUp,long snapshotEnd,int omittedEvents,String redactionPolicy) {
    this(instanceId,sourceZone,records,nextCursor,caughtUp,snapshotEnd,omittedEvents,redactionPolicy,null);
  }
  public LogPage forService(String serviceId) {
    return new LogPage(instanceId,sourceZone,records,nextCursor,caughtUp,snapshotEnd,omittedEvents,redactionPolicy,serviceId);
  }
}
