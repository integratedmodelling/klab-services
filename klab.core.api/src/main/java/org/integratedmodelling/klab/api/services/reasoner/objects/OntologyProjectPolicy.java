package org.integratedmodelling.klab.api.services.reasoner.objects;

import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl;

/** Current project-type contract, independent of any future publication permissions. */
public final class OntologyProjectPolicy {
  public static final String CODE = "ONTOLOGY_PROJECT_REQUIRES_WORLDVIEW";
  private OntologyProjectPolicy() {}

  public static boolean definesWorldview(Project.Manifest manifest) {
    return manifest != null && manifest.getDefinedWorldview() != null
        && !manifest.getDefinedWorldview().isBlank();
  }

  /** Bind the project constraint to this exact document, including an empty declaration body. */
  public static Notification diagnostic(KimOntology document) {
    var context = new NotificationImpl.LexicalContextImpl();
    context.setDocumentUrn(document.getUrn());
    context.setProjectUrn(document.getProjectName());
    context.setDocumentType(KlabAsset.KnowledgeClass.ONTOLOGY);
    context.setOffsetInDocument(0);
    String source = document.getSourceCode();
    int end = source == null ? 0 : source.indexOf('\n');
    context.setLength(source == null ? 0 : Math.max(0, end < 0 ? source.length() : end));
    return Notification.error(CODE + ": Ontology '" + document.getUrn()
        + "' belongs to project '" + document.getProjectName()
        + "', which does not define a worldview. Ontologies are supported only in worldview-defining projects. "
        + "Move this .kwv ontology to a worldview-defining project; use .kim namespaces for models in ordinary projects.", context);
  }
}
