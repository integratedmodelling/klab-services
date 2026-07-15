package org.integratedmodelling.klab.api.lang.kactors.impl;

import java.io.Serial;
import java.util.*;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.KlabLanguage;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.lang.kim.impl.KlabDocumentImpl;

public class KActorsBehaviorImpl extends KlabDocumentImpl<KActorsAction>
    implements KActorsBehavior {

  @Serial private static final long serialVersionUID = 6651874316547941092L;

  public static class ImportImpl implements Import {

    private String importedBehavior;
    private String importedAlias;
    private List<String> importedComponents = new ArrayList<>();

    @Override
    public String getImportedBehavior() {
      return importedBehavior;
    }

    @Override
    public String getImportedAlias() {
      return importedAlias;
    }

    @Override
    public List<String> getImportedComponents() {
      return importedComponents;
    }

    public void setImportedBehavior(String importedBehavior) {
      this.importedBehavior = importedBehavior;
    }

    public void setImportedAlias(String importedAlias) {
      this.importedAlias = importedAlias;
    }

    public void setImportedComponents(List<String> importedComponents) {
      this.importedComponents = importedComponents;
    }
  }

  private String urn;
  private Type behaviorType;
  private Platform platform;
  //  private String output;
  private List<Import> imports = new ArrayList<>();
  private List<KActorsAction> statements = new ArrayList<>();
  //  private String style;
  //  private List<String> locales = new ArrayList<>();
  //  private String label;
  private String description;
  //  private String logo;
  private String projectId;
  private Map<String, String> styleSpecs = new HashMap<>();
  private boolean isPublic;
  private Version version;
  private List<Annotation> annotations = new ArrayList<>();
  private KlabLanguage language = KlabLanguage.K_ACTORS;
  private KlabStatement.Scope scope = KlabStatement.Scope.PRIVATE;

  @Override
  public String getUrn() {
    return this.urn;
  }

  @Override
  public Type getBehaviorType() {
    return this.behaviorType;
  }

  @Override
  public Platform getPlatform() {
    return this.platform;
  }

  //  @Override
  //  public String getOutput() {
  //    return this.output;
  //  }

  @Override
  public List<Import> getImports() {
    return this.imports;
  }

  @Override
  public Set<String> importedNamespaces(boolean withinType) {
    Set<String> ret = new HashSet<>();
    return ret;
  }

  //  @Override
  //  public String getStyle() {
  //    return this.style;
  //  }
  //
  //  @Override
  //  public List<String> getLocales() {
  //    return this.locales;
  //  }

  //  @Override
  //  public String getLabel() {
  //    return this.label;
  //  }

  @Override
  public String getDescription() {
    return this.description;
  }

  //  @Override
  //  public String getLogo() {
  //    return this.logo;
  //  }

  @Override
  public String getProjectId() {
    return this.projectId;
  }

  @Override
  public KlabStatement.Scope getScope() {
    return this.scope;
  }

  //  @Override
  //  public Map<String, String> getStyleSpecs() {
  //    return this.styleSpecs;
  //  }

  //  @Override
  //  public boolean isPublic() {
  //    return this.isPublic;
  //  }

  public void setUrn(String urn) {
    this.urn = urn;
  }

  //  public void setType(Type type) {
  //    this.type = type;
  //  }

  public void setPlatform(Platform platform) {
    this.platform = platform;
  }

  //  public void setOutput(String output) {
  //    this.output = output;
  //  }

  public void setImports(List<Import> imports) {
    this.imports = imports;
  }

  //  public void setStyle(String style) {
  //    this.style = style;
  //  }
  //
  //  public void setLocales(List<String> locales) {
  //    this.locales = locales;
  //  }
  //
  //  public void setLabel(String label) {
  //    this.label = label;
  //  }

  public void setDescription(String description) {
    this.description = description;
  }

  //  public void setLogo(String logo) {
  //    this.logo = logo;
  //  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public void setStyleSpecs(Map<String, String> styleSpecs) {
    this.styleSpecs = styleSpecs;
  }

  public void setPublic(boolean isPublic) {
    this.isPublic = isPublic;
  }

  public Version getVersion() {
    return version;
  }

  @Override
  public List<KActorsAction> getStatements() {
    return this.statements;
  }

  public void setVersion(Version version) {
    this.version = version;
  }

  public void setStatements(List<KActorsAction> statements) {
    this.statements = statements;
  }

  @Override
  public List<Annotation> getAnnotations() {
    return annotations;
  }

  public void setAnnotations(List<Annotation> annotations) {
    this.annotations = annotations;
  }

  @Override
  public KlabLanguage getLanguage() {
    return language;
  }

  public void setLanguage(KlabLanguage language) {
    this.language = language;
  }

  public void setBehaviorType(Type behaviorType) {
    this.behaviorType = behaviorType;
  }

  public Map<String, String> getStyleSpecs() {
    return styleSpecs;
  }

  public boolean isPublic() {
    return isPublic;
  }

  public void setScope(KlabStatement.Scope scope) {
    this.scope = scope;
  }
}
