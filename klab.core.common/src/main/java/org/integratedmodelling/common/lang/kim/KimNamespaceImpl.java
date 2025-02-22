package org.integratedmodelling.common.lang.kim;

import java.io.Serial;
import java.util.*;
import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;

/**
 * The syntactic peer of a k.LAB namespace. To be renamed when we rename the interfaces and the
 * original beans.
 *
 * @author ferdinando.villa
 */
public class KimNamespaceImpl extends KlabDocumentImpl<KlabStatement> implements KimNamespace {

  @Serial private static final long serialVersionUID = 6198296119075476515L;

  private Set<String> disjointNamespaces = new HashSet<>();
  private List<PairImpl<String, String>> owlImports = new ArrayList<>();
  private List<PairImpl<String, List<String>>> vocabularyImports = new ArrayList<>();
  private boolean scenario;
  private String scriptId;
  private String testCaseId;
  private boolean worldviewBound;
  //    private Parameters<String> defines = Parameters.create();
  private List<KlabStatement> statements = new ArrayList<>();
  private Map<String, List<String>> imports = new HashMap<>();
  private Geometry coverage;
  private List<Annotation> annotations = new ArrayList<>();

  @Override
  public Collection<String> getDisjointNamespaces() {
    return this.disjointNamespaces;
  }

  @Override
  public boolean isScenario() {
    return this.scenario;
  }

  @Override
  public String getScriptId() {
    return this.scriptId;
  }

  @Override
  public String getTestCaseId() {
    return this.testCaseId;
  }

  @Override
  public boolean isWorldviewBound() {
    return this.worldviewBound;
  }

  //    @Override
  //    public List<ServiceCall> getExtents() {
  //        return this.extents;
  //    }
  //
  //    @Override
  //    public Parameters<String> getDefines() {
  //        return this.defines;
  //    }

  @Override
  public List<KlabStatement> getStatements() {
    return this.statements;
  }

  @Override
  public Map<String, List<String>> getImports() {
    return this.imports;
  }

  public void setDisjointNamespaces(Set<String> disjointNamespaces) {
    this.disjointNamespaces = disjointNamespaces;
  }

  public void setScenario(boolean scenario) {
    this.scenario = scenario;
  }

  public void setScriptId(String scriptId) {
    this.scriptId = scriptId;
  }

  public void setTestCaseId(String testCaseId) {
    this.testCaseId = testCaseId;
  }

  public void setWorldviewBound(boolean worldviewBound) {
    this.worldviewBound = worldviewBound;
  }

  //    public void setDefines(Parameters<String> defines) {
  //        this.defines = defines;
  //    }

  public void setStatements(List<KlabStatement> statements) {
    this.statements = statements;
  }

  public void setImports(Map<String, List<String>> imports) {
    this.imports = imports;
  }

  @Override
  public Set<String> importedNamespaces(boolean withinType) {
    Set<String> ret = new HashSet<>();
    return ret;
  }

  @Override
  public Geometry getCoverage() {
    return coverage;
  }

  public void setCoverage(Geometry coverage) {
    this.coverage = coverage;
  }

  @Override
  public List<Annotation> getAnnotations() {
    return annotations;
  }

  public void setAnnotations(List<Annotation> annotations) {
    this.annotations = annotations;
  }
}
