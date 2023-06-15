package org.integratedmodelling.klab.api.lang.impl.kactors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;

public class KActorsBehaviorImpl extends KActorsCodeStatementImpl implements KActorsBehavior {

	private static final long serialVersionUID = 6651874316547941092L;

	private String name;
	private Type type;
	private Platform platform;
	private String output;
	private List<String> imports = new ArrayList<>();
	private List<KActorsAction> actions = new ArrayList<>();
	private String style;
	private List<String> locales;
	private String label;
	private String description;
	private String logo;
	private String projectId;
	private Map<String, String> styleSpecs = new HashMap<>();
	private boolean isPublic;
	private String versionString;

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public Type getType() {
		return this.type;
	}

	@Override
	public Platform getPlatform() {
		return this.platform;
	}

	@Override
	public String getOutput() {
		return this.output;
	}

	@Override
	public List<String> getImports() {
		return this.imports;
	}

	@Override
	public List<KActorsAction> getActions() {
		return this.actions;
	}

	@Override
	public String getStyle() {
		return this.style;
	}

	@Override
	public List<String> getLocales() {
		return this.locales;
	}

	@Override
	public String getLabel() {
		return this.label;
	}

	@Override
	public String getDescription() {
		return this.description;
	}

	@Override
	public String getLogo() {
		return this.logo;
	}

	@Override
	public String getProjectId() {
		return this.projectId;
	}

	@Override
	public Map<String, String> getStyleSpecs() {
		return this.styleSpecs;
	}

	@Override
	public boolean isPublic() {
		return this.isPublic;
	}

	@Override
	public String getVersionString() {
		return this.versionString;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public void setPlatform(Platform platform) {
		this.platform = platform;
	}

	public void setOutput(String output) {
		this.output = output;
	}

	public void setImports(List<String> imports) {
		this.imports = imports;
	}

	public void setActions(List<KActorsAction> actions) {
		this.actions = actions;
	}

	public void setStyle(String style) {
		this.style = style;
	}

	public void setLocales(List<String> locales) {
		this.locales = locales;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setLogo(String logo) {
		this.logo = logo;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public void setStyleSpecs(Map<String, String> styleSpecs) {
		this.styleSpecs = styleSpecs;
	}

	public void setPublic(boolean isPublic) {
		this.isPublic = isPublic;
	}

	public void setVersionString(String versionString) {
		this.versionString = versionString;
	}

	@Override
	public void visit(Visitor visitor) {
	}

}
