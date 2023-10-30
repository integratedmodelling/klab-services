package org.integratedmodelling.klab.api.lang.impl.kim;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.lang.kim.KimScope;

public abstract class KimScopeImpl implements KimScope {

	private static final long serialVersionUID = 6072620934600537545L;

	private List<KimScope> children = new ArrayList<>();
	private String locationDescriptor;
//	private String uri;

	@Override
	public List<KimScope> getChildren() {
		return this.children;
	}

	@Override
	public String getLocationDescriptor() {
		return this.locationDescriptor;
	}
//
//	@Override
//	public String getUri() {
//		return this.uri;
//	}

	public void setChildren(List<KimScope> children) {
		this.children = children;
	}

	public void setLocationDescriptor(String locationDescriptor) {
		this.locationDescriptor = locationDescriptor;
	}
//
//	public void setUri(String uri) {
//		this.uri = uri;
//	}

}
