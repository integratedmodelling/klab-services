package org.integratedmodelling.klab.modeler.model;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;

public class NavigableWorldview extends NavigableKlabAsset<Worldview> implements Worldview,
        NavigableContainer {

    @Serial
    private static final long serialVersionUID = 506794106478880781L;

    public NavigableWorldview(Worldview asset) {
        super(asset, null);
        this.path = Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER;
    }

    @Override
    public Collection<KimObservationStrategyDocument> getObservationStrategies() {
        return delegate.getObservationStrategies();
    }

    /**
     * There is only one worldview per user, so we report the workspace conventional ID instead of the
     * worldview name to avoid possible conflicts with workspaces.
     */
    @Override
    public String getUrn() {
        return Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER;
    }

    @Override
    public List<KimOntology> getOntologies() {
        return delegate.getOntologies();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public List<? extends NavigableAsset> createChildren() {

        var ret =
                new ArrayList<NavigableAsset>(delegate.getOntologies().stream().map(ontology -> new NavigableKimOntology(ontology,
                this)).toList());

        if (!delegate.getObservationStrategies().isEmpty()) {
            ret.add(new NavigableFolderImpl<NavigableDocument>("Observation strategies", this) {

                @Override
                protected List<? extends NavigableAsset> createChildren() {
                    return ((Worldview) delegate).getObservationStrategies().stream().map(s -> new NavigableObservationStrategies(s,
                            this)).toList();
                }
            });
        }
        return ret;
    }

    public NavigableKimOntology findOntology(String documentUrn) {
        for (Object child : children()) {
            if (child instanceof NavigableKimOntology ontology && ontology.getUrn().equals(documentUrn)) {
                return ontology;
            }
        }
        return null;
    }

    @Override
    public String getWorldviewId() {
        return delegate.getWorldviewId();
    }
}
