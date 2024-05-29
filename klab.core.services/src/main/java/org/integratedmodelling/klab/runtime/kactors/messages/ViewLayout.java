package org.integratedmodelling.klab.runtime.kactors.messages;

import org.integratedmodelling.klab.api.lang.kactors.beans.Layout;
import org.integratedmodelling.klab.api.services.runtime.kactors.VM;

public class ViewLayout implements VM.AgentMessage {

	private static final long serialVersionUID = -7604315096245266861L;

	private Layout layout;

	public ViewLayout() {}
	
	public ViewLayout(Layout layout) {
		this.layout = layout;
	}
	
	public Layout getLayout() {
		return layout;
	}

	public void setLayout(Layout layout) {
		this.layout = layout;
	}
	
	
	
}
