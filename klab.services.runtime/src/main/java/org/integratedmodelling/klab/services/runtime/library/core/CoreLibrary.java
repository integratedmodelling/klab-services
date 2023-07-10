package org.integratedmodelling.klab.services.runtime.library.core;

import java.util.List;

import org.integratedmodelling.klab.Version;
import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.State;
import org.integratedmodelling.klab.api.services.runtime.extension.Instantiator;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabContextualizer;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabContextualizer.Argument;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.api.services.runtime.extension.Resolver;

@Library(name = "klab.core")
public class CoreLibrary {

	// TODO
	@KlabContextualizer(version = Version.CURRENT, name = "urn.resolver", dataflowLabel = "Resource", description = "Contextualize a quality resource to obtain data", parameters = {
			@Argument(name = "urn", description = "The URN of the resource to contextualize", type = Artifact.Type.TEXT) }, type = Artifact.Type.VALUE)
	public static class ResourceResolver implements Resolver<State> {

		@Override
		public State resolve(State observation, ContextScope scope) {
			// TODO Auto-generated method stub
			return null;
		}

	}

	@KlabContextualizer(version = Version.CURRENT, name = "lut.resolver", dataflowLabel = "Lookup table", description = "Compute outputs by looking up dependency values in a table", parameters = {
			@Argument(name = "urn", description = "The URN of table to use", type = Artifact.Type.TEXT) }, type = Artifact.Type.VALUE)
	public static class LookupTableResolver implements Resolver<State> {

		@Override
		public State resolve(State observation, ContextScope scope) {
			// TODO Auto-generated method stub
			return null;
		}

	}

	@KlabContextualizer(version = Version.CURRENT, name = "urn.instantiator", dataflowLabel = "Resource", description = "Contextualize a Type.OBJECT resource to obtain objects", parameters = {
			@Argument(name = "urn", description = "The URN of the resource to contextualize", type = Artifact.Type.TEXT),
			@Argument(name = "whole", description = "", type = Artifact.Type.BOOLEAN) }, type = Artifact.Type.OBJECT)
	public static class SubjectResourceInstantiator implements Instantiator<DirectObservation> {

		@Override
		public List<DirectObservation> resolve(Observable semantics, ContextScope scope) {
			// TODO Auto-generated method stub
			return null;
		}

	}

}
