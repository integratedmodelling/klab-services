package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl;

/** Immutable diagnostics survive disposal of a rejected candidate without retaining its actor. */
public record CandidateDiagnostic(Notification.Level level, String message, String document,
    String project, KnowledgeClass documentType, KnowledgeClass type, int offset, int length) {
  public static List<CandidateDiagnostic> capture(Collection<Notification> notifications) {
    return notifications.stream().map(notification -> {
      var context = notification.getLexicalContext();
      return new CandidateDiagnostic(notification.getLevel(), notification.getMessage(),
          context == null ? null : context.getDocumentUrn(),
          context == null ? null : context.getProjectUrn(),
          context == null ? null : context.getDocumentType(),
          context == null ? null : context.getType(),
          context == null ? -1 : context.getOffsetInDocument(),
          context == null ? 0 : context.getLength());
    }).distinct().toList();
  }

  public boolean belongsTo(KlabDocument<?> source) {
    return document != null && Objects.equals(document, source.getUrn())
        && Objects.equals(project, source.getProjectName())
        && documentType == KnowledgeClass.classify(source.getClass());
  }

  public Notification notification() {
    if (document == null) return Notification.create(level, message);
    var context = new NotificationImpl.LexicalContextImpl();
    context.setDocumentUrn(document);
    context.setProjectUrn(project);
    context.setDocumentType(documentType);
    context.setType(type);
    context.setOffsetInDocument(offset);
    context.setLength(length);
    return Notification.create(level, message, context);
  }
}
