package org.integratedmodelling.klab.api.lang.kim;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KimMacro.Field;

public interface KimScope extends Serializable {


	List<KimScope> getChildren();

	/**
	 * Return a parseable string that describes the location of this code scope.
	 * 
	 * @return the location
	 */
	String getLocationDescriptor();

	String getUri();

}
