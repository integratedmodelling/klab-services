package org.integratedmodelling.klab.api.services.reasoner.objects;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.knowledge.SemanticType;

/**
 * Sent from the engine to a client when a selection is made by the client from a semantic search,
 * reporting the current status of the observable being built.
 * 
 * @author Ferd
 *
 */
public class SemanticSearchResponse {

    private List<StyledKimToken> code = new ArrayList<>();
    private List<String> errors = new ArrayList<>();
    private int searchId;
    private int requestId;
    private SemanticType currentType;
    private String description;
    private long elapsedTimeMs = System.currentTimeMillis();
    private List<SemanticMatch> matches = new ArrayList<>();
    private int parenthesisDepth;
    private String declaration = "";
    private org.integratedmodelling.klab.api.knowledge.Observable observable;
    private boolean canUndo;
    private boolean canOpenScope;
    private boolean canCloseScope;
    private boolean acceptsValue;

    public String getDeclaration() { return declaration; }
    public void setDeclaration(String value) { declaration = value; }
    /** Non-null only when the complete expression resolves to a satisfiable observable. */
    public org.integratedmodelling.klab.api.knowledge.Observable getObservable() { return observable; }
    public void setObservable(org.integratedmodelling.klab.api.knowledge.Observable value) { observable = value; }
    public boolean isCanUndo() { return canUndo; }
    public void setCanUndo(boolean value) { canUndo = value; }
    public boolean isCanOpenScope() { return canOpenScope; }
    public void setCanOpenScope(boolean value) { canOpenScope = value; }
    public boolean isCanCloseScope() { return canCloseScope; }
    public void setCanCloseScope(boolean value) { canCloseScope = value; }
    public boolean isAcceptsValue() { return acceptsValue; }
    public void setAcceptsValue(boolean value) { acceptsValue = value; }

    public SemanticSearchResponse() {}
    
    public SemanticSearchResponse(int searchId, int requestId) {
        this.searchId = searchId;
        this.requestId = requestId;
    }
    public List<StyledKimToken> getCode() {
        return code;
    }
    public void setCode(List<StyledKimToken> code) {
        this.code = code;
    }
    public List<String> getErrors() {
        return errors;
    }
    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
    public SemanticType getCurrentType() {
        return currentType;
    }
    public void setCurrentType(SemanticType currentType) {
        this.currentType = currentType;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public long getElapsedTimeMs() {
        return elapsedTimeMs;
    }
    public void setElapsedTimeMs(long elapsedTimeMs) {
        this.elapsedTimeMs = elapsedTimeMs;
    }
    public List<SemanticMatch> getMatches() {
        return matches;
    }
    public void setMatches(List<SemanticMatch> matches) {
        this.matches = matches;
    }
    public int getParenthesisDepth() {
        return parenthesisDepth;
    }
    public void setParenthesisDepth(int parenthesisDepth) {
        this.parenthesisDepth = parenthesisDepth;
    }
    public int getSearchId() {
        return searchId;
    }
    public void setSearchId(int searchId) {
        this.searchId = searchId;
    }
    public int getRequestId() {
        return requestId;
    }
    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

}
