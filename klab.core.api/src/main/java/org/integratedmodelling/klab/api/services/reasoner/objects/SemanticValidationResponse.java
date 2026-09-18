package org.integratedmodelling.klab.api.services.reasoner.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Semantic diagnostics have separate ownership from parsing diagnostics in editor clients. */
public class SemanticValidationResponse implements Serializable {
  public enum Status { COMPLETE, SYNTAX_ERRORS, UNAVAILABLE, STALE_KNOWLEDGE, FAILED }
  private Status status = Status.UNAVAILABLE;
  private String documentUrn;
  private String documentVersion;
  private String sourceHash;
  private long knowledgeRevision = -1;
  private String reason;
  private List<Notification> notifications = new ArrayList<>();

  public static SemanticValidationResponse forRequest(SemanticValidationRequest request) {
    var ret = new SemanticValidationResponse();
    ret.documentUrn = request.document().getUrn();
    ret.documentVersion = request.getDocumentVersion();
    ret.sourceHash = SemanticValidationRequest.sourceHash(request.document().getSourceCode());
    return ret;
  }

  /** A service failure has document context, not a fictitious faulty source occurrence. */
  public void addDocumentDiagnostic(SemanticValidationRequest request, Notification.Level level) {
    var context = new org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl.LexicalContextImpl();
    var document = request.document();
    context.setDocumentUrn(document.getUrn());
    context.setProjectUrn(document.getProjectName());
    context.setDocumentType(org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.classify(document.getClass()));
    context.setOffsetInDocument(0);
    context.setLength(1);
    notifications.add(Notification.create(level, reason, context));
  }

  /** Collapse repeated diagnostics for one occurrence, preserving separate source locations. */
  public void deduplicateNotifications() {
    record Occurrence(Notification.Level level, String message, String document, String project,
                      int offset, int length) {}
    var distinct = new java.util.LinkedHashMap<Occurrence, Notification>();
    for (var notification : notifications) {
      var context = notification.getLexicalContext();
      var key = new Occurrence(notification.getLevel(), notification.getMessage(),
          context == null ? documentUrn : context.getDocumentUrn(),
          context == null ? null : context.getProjectUrn(),
          context == null ? -1 : context.getOffsetInDocument(),
          context == null ? -1 : context.getLength());
      distinct.putIfAbsent(key, notification);
    }
    notifications = new ArrayList<>(distinct.values());
  }

  public boolean valid() {
    return status == Status.COMPLETE && notifications.stream()
        .noneMatch(n -> n.getLevel() == Notification.Level.Error);
  }

  /** Check before replacing editor markers; a save or knowledge change may supersede this reply. */
  public boolean matches(SemanticValidationRequest current, long currentKnowledgeRevision) {
    return Objects.equals(documentUrn, current.document().getUrn())
        && Objects.equals(documentVersion, current.getDocumentVersion())
        && Objects.equals(sourceHash, SemanticValidationRequest.sourceHash(current.document().getSourceCode()))
        && knowledgeRevision == currentKnowledgeRevision;
  }

  public Status getStatus() { return status; }
  public void setStatus(Status status) { this.status = status; }
  public String getDocumentUrn() { return documentUrn; }
  public void setDocumentUrn(String urn) { documentUrn = urn; }
  public String getDocumentVersion() { return documentVersion; }
  public void setDocumentVersion(String version) { documentVersion = version; }
  public String getSourceHash() { return sourceHash; }
  public void setSourceHash(String hash) { sourceHash = hash; }
  public long getKnowledgeRevision() { return knowledgeRevision; }
  public void setKnowledgeRevision(long revision) { knowledgeRevision = revision; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public List<Notification> getNotifications() { return notifications; }
  public void setNotifications(List<Notification> notifications) { this.notifications = notifications; }
}
