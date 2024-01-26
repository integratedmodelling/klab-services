package org.integratedmodelling.klab.api.lang.impl.kim;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author Ferd
 *
 */
public abstract class KimStatementImpl /*extends KimScopeImpl*/ implements KlabStatement {

    @Serial
    private static final long serialVersionUID = -7273214821906819558L;
    private int offsetInDocument;
    private int length;
    private List<Annotation> annotations = new ArrayList<>();
    private String deprecation;
    private boolean deprecated;
    private Metadata metadata = Metadata.create();
    private String namespace;
    private Scope scope = Scope.PUBLIC;
    private List<Notification> notifications = new ArrayList<>();

    public KimStatementImpl() {

    }
    protected KimStatementImpl(KimStatementImpl other) {
        this.offsetInDocument = other.offsetInDocument;
        this.length = other.length;
        this.annotations.addAll(other.annotations);
        this.deprecated = other.deprecated;
        this.deprecation = other.deprecation;
        this.metadata = other.metadata;
//        this.documentationMetadata = other.documentationMetadata;
        this.namespace = other.namespace;
        this.scope = other.scope;
        this.notifications.addAll(other.notifications);
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
        return "DIOCAN IMPLEMENTAMI";
    }
    @Override
    public Metadata getMetadata() {
        return this.metadata;
    }

////    @Override
////    public void visit(Visitor visitor) {
////        // TODO Auto-generated method stub
////
//    }
//
//    @Override
//    public Parameters<String> getDocumentationMetadata() {
//        return this.documentationMetadata;
//    }

    @Override
    public String getNamespace() {
        return this.namespace;
    }

    @Override
    public Scope getScope() {
        return this.scope;
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
//
//    public void setDocumentationMetadata(Parameters<String> documentationMetadata) {
//        this.documentationMetadata = documentationMetadata;
//    }

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
        if (sourceCode() != null) {
            return sourceCode();
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

    @Override
    public int getOffsetInDocument() {
        return offsetInDocument;
    }

    public void setOffsetInDocument(int offsetInDocument) {
        this.offsetInDocument = offsetInDocument;
    }

    @Override
    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }
}
