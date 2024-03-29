package org.integratedmodelling.klab.api.lang.kactors.beans;

public class ViewPanel extends ViewComponent {
	
	public enum Location {
		Header,
		Footer,
		MainPanel,
		RightPanel,
		CenterPanel,
	}

	public ViewPanel() {}
	
	public ViewPanel(String name, String style) {
		setName(name);
		setId(name);
		setStyle(style);
	}

}