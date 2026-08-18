package org.integratedmodelling.klab.modeler.model;

import java.io.File;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableFolder;

public class NavigableProject extends NavigableKlabAsset<Project> implements Project {

  @Serial private static final long serialVersionUID = -6759189347982834877L;

  private boolean locked;
  private File rootDirectory;
  private RepositoryState repositoryState;

  public NavigableProject(Project asset, NavigableKlabAsset<?> parent) {
    super(asset, parent);
  }

  @Override
  public List<KActorsBehavior> getApps() {
    return delegate.getApps();
  }

  @Override
  public List<KActorsBehavior> getBehaviors() {
    return delegate.getBehaviors();
  }

  @Override
  public List<KActorsBehavior> getScripts() {
    return delegate.getScripts();
  }

  @Override
  public Manifest getManifest() {
    return delegate.getManifest();
  }

  @Override
  public List<KimNamespace> getNamespaces() {
    return delegate.getNamespaces();
  }

  @Override
  public List<Notification> getNotifications() {
    return delegate.getNotifications();
  }

  @Override
  public List<KimObservationStrategyDocument> getObservationStrategies() {
    return delegate.getObservationStrategies();
  }

  @Override
  public List<KimOntology> getOntologies() {
    return delegate.getOntologies();
  }

  @Override
  public List<String> getResourceUrns() {
    return delegate.getResourceUrns();
  }

  @Override
  public Collection<Annotation> getAnnotations() {
    return delegate.getAnnotations();
  }

  @Override
  public List<KActorsBehavior> getTestCases() {
    return delegate.getTestCases();
  }

  public List<NavigableKlabDocument<?, ?>> documents() {
    List<NavigableKlabDocument<?, ?>> ret = new ArrayList<>();
    for (var child : children()) {
      if (child instanceof NavigableKlabDocument doc) {
        ret.add(doc);
      }
    }
    return ret;
  }

  @Override
  protected List<NavigableAsset> createChildren() {

    // TODO add everything else, including intermediate containers
    var ret =
        new ArrayList<NavigableAsset>(
            getOntologies().stream().map(p -> new NavigableKimOntology(p, this)).toList());

    final Project project = getDelegate();

    ret.addAll(getNamespaces().stream().map(n -> new NavigableKimNamespace(n, this)).toList());

    if (!delegate.getBehaviors().isEmpty()) {
      ret.add(new BehaviorFolder(this));
    }

    if (!delegate.getApps().isEmpty()) {
      ret.add(
          new NavigableFolderImpl<NavigableDocument>("Applications", this) {
            @Override
            protected List<NavigableAsset> createChildren() {
              return project.getApps().stream()
                  .map(s -> (NavigableAsset) (new NavigableKActorsBehavior(s, this)))
                  .toList();
            }
          });
    }
    if (!delegate.getTestCases().isEmpty()) {
      ret.add(
          new NavigableFolderImpl<NavigableDocument>("Test cases", this) {
            @Override
            protected List<NavigableAsset> createChildren() {
              return project.getTestCases().stream()
                  .map(s -> (NavigableAsset) (new NavigableKActorsBehavior(s, this)))
                  .toList();
            }
          });
    }

    if (!delegate.getScripts().isEmpty()) {
      ret.add(
          new NavigableFolderImpl<NavigableDocument>("Scripts", this) {
            @Override
            protected List<NavigableAsset> createChildren() {
              return project.getScripts().stream()
                  .map(s -> (NavigableAsset) (new NavigableKActorsBehavior(s, this)))
                  .toList();
            }
          });
    }

    // observation strategies
    if (!delegate.getObservationStrategies().isEmpty()) {
      ret.add(
          new NavigableFolderImpl<NavigableDocument>("Observation strategies", this) {

            @Override
            protected List<NavigableAsset> createChildren() {
              return project.getObservationStrategies().stream()
                  .map(s -> (NavigableAsset) (new NavigableObservationStrategies(s, this)))
                  .toList();
            }
          });
    }

    return ret;
  }

  @Override
  public RepositoryState getRepositoryState() {
    return repositoryState == null ? delegate.getRepositoryState() : repositoryState;
  }

  public void setRepositoryState(RepositoryState repositoryState) {
    this.repositoryState = repositoryState;
  }

  public RepositoryState.Status computeStatus(KlabDocument<?> document) {
    // Compute the passed document's status re: the repository
    return RepositoryState.Status.UNTRACKED;
  }

  //	@Override
  //	public Repository getRepository() {
  //		return delegate.getRepository();
  //	}

  public boolean isLocked() {
    return this.locked;
  }

  public void setLocked(boolean locked) {
    this.locked = locked;
  }

  /**
   * Local filesystem directory. Only available if project is locked, either by interacting with the
   * very files in the filesystem or through a mirror copy from the service.
   *
   * @return
   */
  public File getRootDirectory() {
    return rootDirectory;
  }

  public void setRootDirectory(File rootDirectory) {
    this.rootDirectory = rootDirectory;
  }

  public NavigableFolder requireFolderFor(KlabDocument<?> document) {

    var folderName =
        switch (document) {
          case KActorsBehavior behavior -> {
            if (behavior.getBehaviorType() == KActorsBehavior.Type.APP) {
              yield AppFolder.TITLE;
            } else if (behavior.getBehaviorType() == KActorsBehavior.Type.SCRIPT) {
              yield ScriptFolder.TITLE;
            } else if (behavior.getBehaviorType() == KActorsBehavior.Type.UNITTEST) {
              yield TestCaseFolder.TITLE;
            }
            yield BehaviorFolder.TITLE;
          }
          case KimObservationStrategyDocument strategy -> ObservationStrategiesFolder.TITLE;
          default -> null;
        };

    if (folderName == null) {
      return null;
    }

    NavigableFolder existing =
        (NavigableFolder)
            children().stream()
                .filter(
                    f -> f instanceof NavigableFolder folder && folder.getUrn().equals(folderName))
                .findFirst()
                .orElse(null);

    if (existing == null) {
      existing =
          switch (document) {
            case KActorsBehavior behavior -> {
              if (behavior.getBehaviorType() == KActorsBehavior.Type.APP) {
                yield new AppFolder(this);
              } else if (behavior.getBehaviorType() == KActorsBehavior.Type.SCRIPT) {
                yield new ScriptFolder(this);
              } else if (behavior.getBehaviorType() == KActorsBehavior.Type.UNITTEST) {
                yield new TestCaseFolder(this);
              }
              yield new BehaviorFolder(this);
            }
            case KimObservationStrategyDocument strategy -> new ObservationStrategiesFolder(this);
            default -> throw new KlabInternalErrorException("cannot handle " + document.getClass());
          };
      addChild(existing);
    }
    return existing;
  }
}
