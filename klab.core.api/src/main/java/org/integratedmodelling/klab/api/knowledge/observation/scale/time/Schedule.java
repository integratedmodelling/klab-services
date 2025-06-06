package org.integratedmodelling.klab.api.knowledge.observation.scale.time;

/**
 * An object that describes a temporal schedule, with potentially more than one extents and
 * resolutions.
 */
public class Schedule {

  private long start;
  private long end;
  private Time.Resolution finestResolution;

  public long getStart() {
    return start;
  }

  public void setStart(long start) {
    this.start = start;
  }

  public long getEnd() {
    return end;
  }

  public void setEnd(long end) {
    this.end = end;
  }

  public Time.Resolution getFinestResolution() {
    return finestResolution;
  }

  public void setFinestResolution(Time.Resolution finestResolution) {
    this.finestResolution = finestResolution;
  }
}
