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
import org.integratedmodelling.klab.api.lang.kim.impl.StatementImpl;

public class KActorsBehaviorImpl extends KlabDocumentImpl<KActorsAction>
    implements KActorsBehavior {

  @Serial private static final long serialVersionUID = 6651874316547941092L;

  public static class ImportImpl extends StatementImpl implements Import {

    private String importedBehavior;
    private String importedAlias;
    private List<String> importedComponents = new ArrayList<>();

    public ImportImpl() {}

    public ImportImpl(String urn) {
      this.importedBehavior = urn;
    }

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
  private List<Import> imports = new ArrayList<>();
  private List<KActorsAction> statements = new ArrayList<>();
  private String description;
  private boolean isPublic;
  private Version version;
  private List<Annotation> annotations = new ArrayList<>();
  private KlabLanguage language = KlabLanguage.K_ACTORS;
  private KlabStatement.Scope scope = KlabStatement.Scope.PRIVATE;
  private List<Import> inheritedBehaviors = new ArrayList<>();

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

  @Override
  public List<Import> getImports() {
    return this.imports;
  }

  @Override
  public List<Import> getInheritedBehaviors() {
    return inheritedBehaviors;
  }

  public void setInheritedBehaviors(List<Import> inheritedBehaviors) {
    this.inheritedBehaviors = inheritedBehaviors;
  }

  @Override
  public Set<String> importedNamespaces(boolean withinType) {
    Set<String> ret = new HashSet<>();
    return ret;
  }

  @Override
  public String getDescription() {
    return this.description;
  }

  @Override
  public KlabStatement.Scope getScope() {
    return this.scope;
  }

  public void setUrn(String urn) {
    this.urn = urn;
  }

  public void setPlatform(Platform platform) {
    this.platform = platform;
  }

  public void setImports(List<Import> imports) {
    this.imports = imports;
  }

  public void setDescription(String description) {
    this.description = description;
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

  public boolean isPublic() {
    return isPublic;
  }

  public void setScope(KlabStatement.Scope scope) {
    this.scope = scope;
  }
}
