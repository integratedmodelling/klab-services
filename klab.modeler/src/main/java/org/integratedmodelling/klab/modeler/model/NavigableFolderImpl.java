package org.integratedmodelling.klab.modeler.model;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableFolder;

import java.util.Collection;

public abstract class NavigableFolderImpl<T extends KlabAsset> extends NavigableKlabAsset<T>
    implements NavigableFolder {

  private String name;
  public Metadata metadata = Metadata.create();

  public NavigableFolderImpl(String name, NavigableKlabAsset<?> parent) {
    super(name, parent);
    this.name = name;
  }

  @Override
  protected boolean is(KlabAsset asset) {
    return asset == this;
  }

  @Override
  public String getUrn() {
    return name;
  }

  @Override
  public Collection<Annotation> getAnnotations() {
    return delegate.getAnnotations();
  }

  @Override
  public Metadata getMetadata() {
    return metadata;
  }
}
