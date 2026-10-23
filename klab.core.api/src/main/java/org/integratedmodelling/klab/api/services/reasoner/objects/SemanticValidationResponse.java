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
  private String authoringSource;
  private String documentVersion;
  private String sourceHash;
  private long knowledgeRevision = -1;
  private boolean executableModels;
  private String executionContext;
  private boolean classifierPreparableDependencies;
  public record PendingClassifierDependency(String modelUrn, int dependencyIndex, String originalObservable,
      String residualObservable, java.util.List<java.util.List<String>> occurrences) implements Serializable {
    public PendingClassifierDependency { occurrences=occurrences.stream().map(java.util.List::copyOf).toList(); }
  }
  private List<PendingClassifierDependency> pendingClassifierDependencies=new ArrayList<>();
  public List<PendingClassifierDependency> getPendingClassifierDependencies() { return pendingClassifierDependencies; }
  public void setPendingClassifierDependencies(List<PendingClassifierDependency> value) { pendingClassifierDependencies=value; }

  /** Authored source obligations only. Executable admission recomputes these against actual context. */
  public record ConditionalModelContext(String modelUrn, List<String> requiredContextAlternatives) implements Serializable {
    public ConditionalModelContext { requiredContextAlternatives=List.copyOf(requiredContextAlternatives); }
  }
  private List<ConditionalModelContext> conditionalModelContexts=new ArrayList<>();
  public List<ConditionalModelContext> getConditionalModelContexts() { return conditionalModelContexts; }
  public void setConditionalModelContexts(List<ConditionalModelContext> value) { conditionalModelContexts=value; }

  /** Pending conjunction, not an admission receipt: actual context must specialize the container
   * AND prove every referenced dependency's complete bearer clauses at execution revision. */
  public record PendingModelContext(String modelUrn, String containerUrn,
      List<String> dependentSemanticUrns) implements Serializable {
    public PendingModelContext { dependentSemanticUrns=List.copyOf(dependentSemanticUrns); }
  }
  private List<PendingModelContext> pendingModelContexts=new ArrayList<>();
  public List<PendingModelContext> getPendingModelContexts() { return pendingModelContexts; }
  public void setPendingModelContexts(List<PendingModelContext> value) { pendingModelContexts=value; }

  /** Saved-source obligation only: no observed container, no completed collection, no runtime admission. */
  public record PendingMemberQualityDependency(String modelUrn,int dependencyIndex,String sourceObservable,
      String port,String nativeBearer,String quality) implements Serializable {}
  private List<PendingMemberQualityDependency> pendingMemberQualityDependencies=new ArrayList<>();
  private List<PendingMemberQualityDependency> preparableMemberQualityDependencies=List.of();
  public List<PendingMemberQualityDependency> getPendingMemberQualityDependencies() { return pendingMemberQualityDependencies; }
  public void setPendingMemberQualityDependencies(List<PendingMemberQualityDependency> value) { pendingMemberQualityDependencies=value==null?new ArrayList<>():new ArrayList<>(value); }
  public List<PendingMemberQualityDependency> getPreparableMemberQualityDependencies() { return preparableMemberQualityDependencies; }
  public void setPreparableMemberQualityDependencies(List<PendingMemberQualityDependency> value) { preparableMemberQualityDependencies=value==null?List.of():List.copyOf(value); }

  private String reason;
  private List<Notification> notifications = new ArrayList<>();

  public static SemanticValidationResponse forRequest(SemanticValidationRequest request) {
    var ret = new SemanticValidationResponse();
    ret.documentUrn = request.document().getUrn();
    if (org.integratedmodelling.klab.api.services.resources.AuthoringSource.id(request.document()) != null)
      ret.authoringSource = org.integratedmodelling.klab.api.services.resources.AuthoringSource.responseIdentity(request.document());
    ret.documentVersion = request.getDocumentVersion();
    ret.executableModels = request.isExecutableModels();
    ret.executionContext = request.getExecutionContext();
    ret.classifierPreparableDependencies = request.isClassifierPreparableDependencies();
    ret.preparableMemberQualityDependencies = List.copyOf(request.getPreparableMemberQualityDependencies());
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
    return (org.integratedmodelling.klab.api.services.resources.AuthoringSource.id(current.document()) == null
        ? authoringSource == null
        : Objects.equals(authoringSource, org.integratedmodelling.klab.api.services.resources.AuthoringSource.responseIdentity(current.document())))
        && Objects.equals(documentUrn, current.document().getUrn())
        && Objects.equals(documentVersion, current.getDocumentVersion())
        && Objects.equals(sourceHash, SemanticValidationRequest.sourceHash(current.document().getSourceCode()))
        && Objects.equals(executionContext, current.getExecutionContext())
        && executableModels == current.isExecutableModels()
        && classifierPreparableDependencies == current.isClassifierPreparableDependencies()
        && Objects.equals(preparableMemberQualityDependencies,current.getPreparableMemberQualityDependencies())
        && knowledgeRevision == currentKnowledgeRevision;
  }

  public boolean isClassifierPreparableDependencies() { return classifierPreparableDependencies; }
  public void setClassifierPreparableDependencies(boolean value) { classifierPreparableDependencies = value; }

  public String getExecutionContext() { return executionContext; }
  public void setExecutionContext(String value) { executionContext = value; }

  public boolean isExecutableModels() { return executableModels; }
  public void setExecutableModels(boolean value) { executableModels = value; }
  public String getAuthoringSource() { return authoringSource; }
  public void setAuthoringSource(String value) { authoringSource = value; }
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
