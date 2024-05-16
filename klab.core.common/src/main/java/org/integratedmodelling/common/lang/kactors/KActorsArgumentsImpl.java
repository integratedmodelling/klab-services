package org.integratedmodelling.common.lang.kactors;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.collections.impl.ParametersImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Arguments;

public class KActorsArgumentsImpl extends ParametersImpl<String> implements Arguments {

	private List<String> metadataKeys = new ArrayList<>();

	private static final long serialVersionUID = -6906711012673497730L;

	@Override
	public List<String> getMetadataKeys() {
		return metadataKeys;
	}

	public void setMetadataKeys(List<String> metadataKeys) {
		this.metadataKeys = metadataKeys;
	}

}
