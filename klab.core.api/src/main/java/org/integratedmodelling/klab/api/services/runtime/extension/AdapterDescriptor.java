package org.integratedmodelling.klab.api.services.runtime.extension;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.resources.adapters.ResourceAdapter;

import java.util.List;
import java.util.Set;

/**
 * Describes an adapter from a client's perspective. Included in component descriptor which is part
 * of the common service capabiities.
 */
public class AdapterDescriptor {

  private String name;
  private Version version;
  private String serviceId;
  private KlabService.Type serviceType;
  private boolean universal;
  private boolean reentrant;
  private boolean contextualizing;
  private boolean sanitizing;
  private boolean inspecting;
  private boolean publishing;
  private boolean embeddable;
  private List<Adapter.Parameter> parameters;
  private Set<ResourceAdapter.Validator.LifecyclePhase> validatedPhases;
  private List<ResourceTransport.Schema> importSchemata;
  private List<ResourceTransport.Schema> exportSchemata;
  private int splits;
  private Data.FillCurve fillCurve;
  private long minSplitSize;

  // do not remove - for the deserializer
  public AdapterDescriptor() {}

  public AdapterDescriptor(
      String name,
      Version version,
      String serviceId,
      KlabService.Type serviceType,
      boolean universal,
      boolean reentrant,
      boolean contextualizing,
      boolean sanitizing,
      boolean inspecting,
      boolean publishing,
      boolean embeddable,
      Data.FillCurve fillCurve,
      int splits,
      long minSplitSize,
      Set<ResourceAdapter.Validator.LifecyclePhase> validatedPhases,
      List<ResourceTransport.Schema> importSchemata,
      List<ResourceTransport.Schema> exportSchemata,
      List<Adapter.Parameter> parameters) {
    this.name = name;
    this.version = version;
    this.serviceId = serviceId;
    this.serviceType = serviceType;
    this.universal = universal;
    this.reentrant = reentrant;
    this.contextualizing = contextualizing;
    this.sanitizing = sanitizing;
    this.inspecting = inspecting;
    this.publishing = publishing;
    this.validatedPhases = validatedPhases;
    this.importSchemata = importSchemata;
    this.exportSchemata = exportSchemata;
    this.embeddable = embeddable;
    this.parameters = parameters;
    this.splits = splits;
    this.fillCurve = fillCurve;
    this.minSplitSize = minSplitSize;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Version getVersion() {
    return version;
  }

  public void setVersion(Version version) {
    this.version = version;
  }

  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  public KlabService.Type getServiceType() {
    return serviceType;
  }

  public void setServiceType(KlabService.Type serviceType) {
    this.serviceType = serviceType;
  }

  public boolean isUniversal() {
    return universal;
  }

  public void setUniversal(boolean universal) {
    this.universal = universal;
  }

  public boolean isReentrant() {
    return reentrant;
  }

  public void setReentrant(boolean reentrant) {
    this.reentrant = reentrant;
  }

  public boolean isContextualizing() {
    return contextualizing;
  }

  public void setContextualizing(boolean contextualizing) {
    this.contextualizing = contextualizing;
  }

  public boolean isSanitizing() {
    return sanitizing;
  }

  public void setSanitizing(boolean sanitizing) {
    this.sanitizing = sanitizing;
  }

  public boolean isInspecting() {
    return inspecting;
  }

  public void setInspecting(boolean inspecting) {
    this.inspecting = inspecting;
  }

  public boolean isPublishing() {
    return publishing;
  }

  public void setPublishing(boolean publishing) {
    this.publishing = publishing;
  }

  public Set<ResourceAdapter.Validator.LifecyclePhase> getValidatedPhases() {
    return validatedPhases;
  }

  public void setValidatedPhases(Set<ResourceAdapter.Validator.LifecyclePhase> validatedPhases) {
    this.validatedPhases = validatedPhases;
  }

  public List<ResourceTransport.Schema> getImportSchemata() {
    return importSchemata;
  }

  public void setImportSchemata(List<ResourceTransport.Schema> importSchemata) {
    this.importSchemata = importSchemata;
  }

  public List<ResourceTransport.Schema> getExportSchemata() {
    return exportSchemata;
  }

  public void setExportSchemata(List<ResourceTransport.Schema> exportSchemata) {
    this.exportSchemata = exportSchemata;
  }

  public List<Adapter.Parameter> getParameters() {
    return parameters;
  }

  public void setParameters(List<Adapter.Parameter> parameters) {
    this.parameters = parameters;
  }

  public boolean isEmbeddable() {
    return embeddable;
  }

  public void setEmbeddable(boolean embeddable) {
    this.embeddable = embeddable;
  }

  public int getSplits() {
    return splits;
  }

  public void setSplits(int splits) {
    this.splits = splits;
  }

  public Data.FillCurve getFillCurve() {
    return fillCurve;
  }

  public void setFillCurve(Data.FillCurve fillCurve) {
    this.fillCurve = fillCurve;
  }

  public long getMinSplitSize() {
    return minSplitSize;
  }

  public void setMinSplitSize(long minSplitSize) {
    this.minSplitSize = minSplitSize;
  }
}
