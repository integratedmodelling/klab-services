package org.integratedmodelling.klab.api.services.runtime.objects;

import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/** Transport bean; callers must not treat a deserialized bean as a runtime admission certificate. */
public class MemberQualityCollectionImpl implements MemberQualityCollection {
  private MemberQualityRequest request;
  private Long observerId;
  private String observerUrn;
  private String observerClass;
  private List<Entry> entries = new ArrayList<>();
  public MemberQualityRequest getRequest() { return request; }
  public void setRequest(MemberQualityRequest value) { request=value; }
  public Long getObserverId() { return observerId; }
  public void setObserverId(Long value) { observerId=value; }
  public String getObserverUrn() { return observerUrn; }
  public void setObserverUrn(String value) { observerUrn=value; }
  public String getObserverClass() { return observerClass; }
  public void setObserverClass(String value) { observerClass=value; }
  public List<Entry> getEntries() { return entries; }
  public void setEntries(List<Entry> value) { entries=new ArrayList<>(value); }
  public static class EntryImpl implements Entry {
    private Observation member;
    private Observation quality;
    public EntryImpl() {}
    public EntryImpl(Observation member, Observation quality) { this.member=member;this.quality=quality; }
    public Observation getMember() { return member; }
    public void setMember(Observation value) { member=value; }
    public Observation getQuality() { return quality; }
    public void setQuality(Observation value) { quality=value; }
  }
}
