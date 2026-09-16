package org.integratedmodelling.klab.api.services.reasoner.objects;

import java.util.EnumSet;
import java.util.Set;

import org.integratedmodelling.klab.api.knowledge.SemanticType;

/**
 * Incremental semantic-search request. Start with searchId=0 and TOKEN (an empty query is valid),
 * then reuse the server-assigned ID. Request IDs must increase within that search. Serialize edits;
 * clients may discard obsolete query responses but must retain the initialized search ID.
 *
 * <p>TOKEN only queries. SELECT accepts selectedMatchId from the response identified by
 * matchesRequestId. VALUE accepts a literal in queryString when the server requests one.
 * UNDO and scope operations edit the expression. cancelSearch releases the session.
 * Initial semanticTypes constrain completed results; initial matchTypes filter proposals.
 * Unsupported components are not offered. Idle searches expire on the server.
 */
public class SemanticSearchRequest {

    public enum Mode {
        TOKEN, UNDO, OPEN_SCOPE, CLOSE_SCOPE, SELECT, VALUE
    }

    private String queryString;
    private int searchId;
    private int requestId;
    private boolean cancelSearch;
    private boolean defaultResults;
    private int maxResults = 9;
    private Set<SemanticType> semanticTypes = EnumSet.noneOf(SemanticType.class);
    private Set<SemanticMatch.Type> matchTypes = EnumSet.noneOf(SemanticMatch.Type.class);
    private Mode searchMode = Mode.TOKEN;
    private String selectedMatchId;
    private int matchesRequestId;

    /** Identity of a proposal in the response identified by matchesRequestId. */
    public String getSelectedMatchId() { return selectedMatchId; }
    public void setSelectedMatchId(String value) { selectedMatchId = value; }
    public int getMatchesRequestId() { return matchesRequestId; }
    public void setMatchesRequestId(int value) { matchesRequestId = value; }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public boolean isCancelSearch() {
        return cancelSearch;
    }

    public void setCancelSearch(boolean cancelSearch) {
        this.cancelSearch = cancelSearch;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public Set<SemanticType> getSemanticTypes() {
        return semanticTypes;
    }

    public void setSemanticTypes(Set<SemanticType> semanticTypes) {
        this.semanticTypes = semanticTypes;
    }

    public Set<SemanticMatch.Type> getMatchTypes() {
        return matchTypes;
    }

    public void setMatchTypes(Set<SemanticMatch.Type> matchTypes) {
        this.matchTypes = matchTypes;
    }

    @Override
    public String toString() {
        return "SearchRequest [queryString=" + queryString + ", contextId=" + searchId + ", requestId=" + requestId
                + ", cancelSearch=" + cancelSearch + ", maxResults=" + maxResults + ", semanticTypes=" + semanticTypes
                + ", matchTypes=" + matchTypes + "]";
    }

    /**
     * Reserved for personalized defaults. Currently empty TOKEN queries return ordinary proposals.
     * 
     * @return
     */
    public boolean isDefaultResults() {
        return defaultResults;
    }

    public void setDefaultResults(boolean defaultResults) {
        this.defaultResults = defaultResults;
    }

    public Mode getSearchMode() {
        return searchMode;
    }

    public void setSearchMode(Mode searchMode) {
        this.searchMode = searchMode;
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
