package org.integratedmodelling.klab.api.lang.impl.kactors;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.lang.impl.kim.KlabDocumentImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;

import java.util.*;

public class KActorsBehaviorImpl extends KlabDocumentImpl<KActorsAction> implements KActorsBehavior {

    private static final long serialVersionUID = 6651874316547941092L;

    private String urn;
    private Type type;
    private Platform platform;
    private String output;
    private List<String> imports = new ArrayList<>();
    private List<KActorsAction> statements = new ArrayList<>();
    private String style;
    private List<String> locales = new ArrayList<>();
    private String label;
    private String description;
    private String logo;
    private String projectId;
    private Map<String, String> styleSpecs = new HashMap<>();
    private boolean isPublic;
    private Version version;

    @Override
    public String getUrn() {
        return this.urn;
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

    //	@Override
    //	public List<KActorsAction> getActions() {
    //		return this.actions;
    //	}
    @Override
    public Set<String> importedNamespaces(boolean withinType) {
        Set<String> ret = new HashSet<>();
        return ret;
    }

    @Override
    public void visit(DocumentVisitor visitor) {

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

    public void setUrn(String urn) {
        this.urn = urn;
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

    //	public void setActions(List<KActorsAction> actions) {
    //		this.actions = actions;
    //	}

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

    public Version getVersion() {
        return version;
    }

    @Override
    public List<KActorsAction> getStatements() {
        return this.statements;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public void setStatements(List<KActorsAction> statements) {
        this.statements = statements;
    }
}
