package org.integratedmodelling.klab.modeler.model;

import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NavigableObservationStrategies extends NavigableKlabDocument<KimObservationStrategy, KimObservationStrategyDocument> implements KimObservationStrategyDocument{

	@Serial
	private static final long serialVersionUID = 3213955882357790089L;

	public NavigableObservationStrategies(KimObservationStrategyDocument document, NavigableKlabAsset<?> parent) {
		super(document, parent);
	}

	@Override
	protected List<? extends NavigableKlabStatement<KimObservationStrategy>> createChildren() {
		return getStatements().stream().map(s -> new NavigableObservationStrategy(s, this)).toList();
	}

	@Override
	public Collection<Annotation> getAnnotations() { return delegate.getAnnotations(); }

	@Override
	public Set<String> importedNamespaces(boolean withinType) {
		return delegate.importedNamespaces(withinType);
	}

}
