package org.integratedmodelling.klab.services.resolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Prioritizer;

/**
 * A model list that iterates in priority order. Only iterator() is redefined
 * this way.
 */
public class PrioritizedList extends ArrayList<Model> {

	private static final long serialVersionUID = -2045768445348955527L;

	private Prioritizer<Model> prioritizer;

	public PrioritizedList(ContextScope scope, Scale scale) {
		this.prioritizer = new PrioritizerImpl(scope, scale);
	}

	@Override
	public Iterator<Model> iterator() {
		Collections.sort(this, this.prioritizer);
		return super.iterator();
	}

}
