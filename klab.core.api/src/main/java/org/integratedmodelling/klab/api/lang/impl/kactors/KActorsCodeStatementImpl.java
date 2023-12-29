package org.integratedmodelling.klab.api.lang.impl.kactors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsCodeStatement;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;

public class KActorsCodeStatementImpl implements KActorsCodeStatement 	{

	private static final long serialVersionUID = -3317041109502570786L;
    private int firstLine;
    private int lastLine;
    private int firstCharOffset;
    private int lastCharOffset;
    private List<Annotation> annotations = new ArrayList<>();
    private List<Notification> notifications = new ArrayList<>();
    private String deprecation;
    private boolean deprecated;
    private String sourceCode;
//    private boolean errors;
//    private boolean warnings;
    private Metadata metadata;
    private String tag;
    
    @Override
    public int getFirstLine() {
        return this.firstLine;
    }

    @Override
    public int getLastLine() {
        return this.lastLine;
    }

    @Override
    public int getFirstCharOffset() {
        return this.firstCharOffset;
    }

    @Override
    public int getLastCharOffset() {
        return this.lastCharOffset;
    }

    @Override
    public List<Annotation> getAnnotations() {
        return this.annotations;
    }

    @Override
    public String getDeprecation() {
        return this.deprecation;
    }

    @Override
    public boolean isDeprecated() {
        return this.deprecated;
    }

    @Override
    public String sourceCode() {
        return this.sourceCode;
    }

//    @Override
//    public boolean isErrors() {
//        return this.errors;
//    }
//
//    @Override
//    public boolean isWarnings() {
//        return this.warnings;
//    }

    @Override
    public Metadata getMetadata() {
        return this.metadata;
    }

    public void setFirstLine(int firstLine) {
        this.firstLine = firstLine;
    }

    public void setLastLine(int lastLine) {
        this.lastLine = lastLine;
    }

    public void setFirstCharOffset(int firstCharOffset) {
        this.firstCharOffset = firstCharOffset;
    }

    public void setLastCharOffset(int lastCharOffset) {
        this.lastCharOffset = lastCharOffset;
    }

    public void setAnnotations(List<Annotation> annotations) {
        this.annotations = annotations;
    }

    public void setDeprecation(String deprecation) {
        this.deprecation = deprecation;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

//    public void setErrors(boolean errors) {
//        this.errors = errors;
//    }
//
//    public void setWarnings(boolean warnings) {
//        this.warnings = warnings;
//    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        if (sourceCode() != null) {
            return sourceCode();
        }
        return Utils.Paths.getLast(this.getClass().getCanonicalName(), '.') + " (no source available)";
    }

    @Override
	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	@Override
	public void visit(Visitor visitor) {
		// TODO Auto-generated method stub
		
	}
	
    @Override
    public Collection<Notification> getNotifications() {
        return notifications;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
    }
}
