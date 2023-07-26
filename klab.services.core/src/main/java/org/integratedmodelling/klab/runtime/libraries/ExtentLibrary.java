package org.integratedmodelling.klab.runtime.libraries;

import org.integratedmodelling.klab.Version;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabContextualizer;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabContextualizer.Argument;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;

@Library(name = Library.CORE_LIBRARY, description = "Core extent functions to declare space and time extents")
public class ExtentLibrary {

	@KlabContextualizer(name = "space", description = "Create spatial extents of all supported types", type = Type.SPATIALEXTENT, version = Version.CURRENT, 
			parameters = { 
					@Argument(name = "shape", type = Type.TEXT, description="A geometric shape in WKT or WKB form")
			}
	)
	public static Space space() {
		return null;
	}

}
