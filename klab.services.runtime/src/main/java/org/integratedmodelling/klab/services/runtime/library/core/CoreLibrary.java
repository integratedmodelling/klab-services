package org.integratedmodelling.klab.services.runtime.library.core;

import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.services.runtime.extension.Instantiator;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabContextualizer;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabContextualizer.Argument;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.api.services.runtime.extension.Resolver;

@Library(name = "klab.core")
public class CoreLibrary {

	// TODO
	@KlabContextualizer(name = "urn.resolver", dataflowLabel = "Resource", description = "Contextualize a quality resource to obtain data", parameters = {
			@Argument(name = "urn", description = "The URN of the resource to contextualize", type = Artifact.Type.TEXT) }, type = Artifact.Type.VALUE)
	public static class ResourceContextualizer implements Resolver {

	}

	@KlabContextualizer(name = "urn.instantiator", dataflowLabel = "Resource", description = "Contextualize a Type.OBJECT resource to obtain objects", parameters = {
			@Argument(name = "urn", description = "The URN of the resource to contextualize", type = Artifact.Type.TEXT),
			@Argument(name = "whole", description = "", type = Artifact.Type.BOOLEAN) }, type = Artifact.Type.OBJECT)
	public static class ResourceInstantiator implements Instantiator {

	}

}
