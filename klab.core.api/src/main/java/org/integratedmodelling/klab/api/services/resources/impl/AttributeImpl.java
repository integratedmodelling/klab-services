package org.integratedmodelling.klab.api.services.resources.impl;

import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.knowledge.Resource;

public class AttributeImpl implements Resource.Attribute {

	private String name;
	private Type type;
	private boolean key;
	private boolean optional;
	private int index;

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public Type getType() {
		return this.type;
	}

	@Override
	public boolean isKey() {
		return this.key;
	}

	@Override
	public boolean isOptional() {
		return this.optional;
	}

	@Override
	public int getIndex() {
		return this.index;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public void setKey(boolean key) {
		this.key = key;
	}

	public void setOptional(boolean optional) {
		this.optional = optional;
	}

	public void setIndex(int index) {
		this.index = index;
	}

}
