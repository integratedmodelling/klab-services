package org.integratedmodelling.klab.api.lang.impl.kim;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.KimStatement;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;

/**
 * 
 * @author Ferd
 *
 */
public abstract class KimStatementImpl extends KimScopeImpl implements KimStatement {

    private static final long serialVersionUID = -7273214821906819558L;
    private int firstLine;
    private int lastLine;
    private int firstCharOffset;
    private int lastCharOffset;
    private List<Annotation> annotations = new ArrayList<>();
    private String deprecation;
    private boolean deprecated;
    private String sourceCode;
//    private boolean errors;
//    private boolean warnings;
    private Metadata metadata;
    private Parameters<String> documentationMetadata;
    private String namespace;
    private Scope scope;
    private List<Notification> notifications = new ArrayList<>();
//    private String kimStatementClass;
    
//    public KimStatement() {
//        this.kimStatementClass = Utils.Paths.getLast(this.getClass().getCanonicalName(), '.');
//    }
    
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
    public String getSourceCode() {
        return this.sourceCode;
    }
//
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

    @Override
    public void visit(Visitor visitor) {
        // TODO Auto-generated method stub

    }

    @Override
    public Parameters<String> getDocumentationMetadata() {
        return this.documentationMetadata;
    }

    @Override
    public String getNamespace() {
        return this.namespace;
    }

    @Override
    public Scope getScope() {
        return this.scope;
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

    public void setDocumentationMetadata(Parameters<String> documentationMetadata) {
        this.documentationMetadata = documentationMetadata;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

//    public String getKimStatementClass() {
//        return kimStatementClass;
//    }
//
//    public void setKimStatementClass(String kimStatementClass) {
//        this.kimStatementClass = kimStatementClass;
//    }

    @Override
    public String toString() {
        if (getSourceCode() != null) {
            return getSourceCode();
        }
        return Utils.Paths.getLast(this.getClass().getCanonicalName(), '.') + " (no source available)";
    }

    @Override
    public List<Notification> getNotifications() {
        return notifications;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
    }
    
}
