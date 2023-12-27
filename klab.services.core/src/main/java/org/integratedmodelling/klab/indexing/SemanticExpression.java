package org.integratedmodelling.klab.indexing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.integratedmodelling.klab.api.data.mediation.Currency;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticRole;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.BinarySemanticOperator;
import org.integratedmodelling.klab.api.lang.SemanticLexicalElement;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.StyledKimToken;
import org.integratedmodelling.klab.indexing.SemanticScope.Constraint;
import org.integratedmodelling.klab.utilities.Utils;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;

/**
 * A semantic expression represented as a graph, built one token at a time, corresponding to the incremental building of
 * an observable by an interactive user. The reasoner exposes methods to initialize a semantic expression and provide
 * contextual suggestions for tokens that can be added without causing inconsistent observables.
 *
 * @author Ferd
 */
public class SemanticExpression {

    public class SemanticToken {

        public Concept concept;
        public Unit unit;
        public Currency currency;
        public Object value;
        public Stack<SemanticToken> previous = new Stack<>();
        private SemanticScope scope;

        SemanticToken() {
        }

        SemanticToken(SemanticToken previous) {
            this.previous.push(previous);
        }

        public boolean isEmpty() {
            return concept == null && unit == null && currency == null && value == null;
        }

        @Override
        public String toString() {
            if (concept != null) {
                return "<" + concept + "> Admits: " + scope;
            } else if (unit != null) {
                return "<" + unit + "> Admits: " + scope;
            } else if (currency != null) {
                return "<" + currency + "> Admits: " + scope;
            } else if (value != null) {
                return "<" + value + "> Admits: " + scope;
            }

            return "<empty> " + scope;
        }

        /**
         * True if the content match the object.
         *
         * @param o
         * @return
         */
        public boolean is(Object o) {
            return false;
        }

        public SemanticScope getScope() {
            return scope;
        }

        public SemanticToken getGroupParent() {
            for (SemanticLink link : graph.incomingEdgesOf(this)) {
                if (link.observableRole == SemanticRole.GROUP_OPEN) {
                    return graph.getEdgeSource(link);
                }
            }
            for (SemanticLink link : graph.incomingEdgesOf(this)) {
                SemanticToken ret = graph.getEdgeSource(link).getGroupParent();
                if (ret != null) {
                    return ret;
                }
            }
            return null;
        }

        /**
         * True if the context matches the role, i.e. we are linked to another node through it. The link is checked
         * upstream if the closest link is a group.
         *
         * @param role
         * @return
         */
        public boolean isAs(SemanticRole role) {
            for (SemanticLink link : graph.incomingEdgesOf(this)) {
                if (link.is(SemanticRole.GROUP_OPEN) && role != SemanticRole.GROUP_OPEN) {
                    if (graph.getEdgeSource(link).isAs(role)) {
                        return true;
                    } else if (link.is(role)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public Observable buildObservable() {
            String declaration = buildDeclaration();
            if (declaration.isBlank() || declaration.contains("?")) {
                return null;
            }
            try {
                return SemanticExpression.this.scope.getService(Reasoner.class).resolveObservable(declaration);
            } catch (Throwable t) {
                //
            }
            return null;
        }

        public Concept buildConcept() {
            Observable observable = buildObservable();
            return observable == null ? null : observable.getSemantics();
        }

        public String buildDeclaration() {
            List<StyledKimToken> ret = new ArrayList<>();
            collectStyledCode(this, ret);
            return joinTokens(ret);
        }

    }

    public class SemanticLink {

        public org.integratedmodelling.klab.api.knowledge.SemanticRole observableRole;
        public ValueOperator valueOperator;
        public UnarySemanticOperator unarySemanticOperator;
        public BinarySemanticOperator binarySemanticOperator;
        public SemanticLexicalElement semanticModifier;
        public String syntacticElement;

        public boolean is(SemanticRole role) {
            return role.equals(this.observableRole);
        }

        @Override
        public String toString() {
            if (valueOperator != null) {
                return "[" + valueOperator + "]";
            } else if (unarySemanticOperator != null) {
                return "[" + unarySemanticOperator + "]";
            } else if (binarySemanticOperator != null) {
                return "[" + binarySemanticOperator + "]";
            } else if (semanticModifier != null) {
                return "[" + semanticModifier + "]";
            } else if (syntacticElement != null) {
                return "[" + syntacticElement + "]";
            } else if (observableRole != null) {
                return "[" + observableRole + "]";
            }
            return "FUCK";
        }

    }

    private SemanticToken head;
    private SemanticToken current;
    private String error = null;
    private Graph<SemanticToken, SemanticLink> graph = new DefaultDirectedGraph<>(SemanticLink.class);
    private Map<String, Object> data = new HashMap<>();
    private Scope scope;

    private SemanticExpression(Scope scope) {
        this.scope = scope;
        this.current = this.head = new SemanticToken();
        this.current.scope = SemanticScope.root();
        this.graph.addVertex(this.head);
    }

    public String joinTokens(List<StyledKimToken> ret) {

        StringBuffer text = new StringBuffer(512);
        StyledKimToken previous = null;
        for (StyledKimToken token : ret) {
            if (token.isNeedsWhitespaceBefore() && text.length() > 0) {
                // there's probably a more readable way to say this
                if (!(previous != null && !previous.isNeedsWhitespaceAfter())) {
                    text.append(" ");
                }
            }
            int a = text.length();
            text.append(token.getValue());
            int b = text.length();
            if (token.getColor() != null && token.getFont() != null) {
                // StyleRange style = new StyleRange();
                // style.start = a;
                // style.length = b - a;
                // style.foreground = getColor(token.getColor());
                // style.fontStyle = getFont(token.getFont());
                // styles.add(style);
            }
            previous = token;
        }

        return text.toString();
    }

    public SemanticToken getCurrent() {
        return current;
    }

    public boolean isError() {
        return this.error != null;
    }

    public String getErrorAndReset() {
        String ret = error;
        this.error = null;
        return ret;
    }

    /**
     * Undo the last accepted token.
     *
     * @return
     */
    public boolean undo() {

        if (this.current.previous.isEmpty()) {
            return false;
        }
        SemanticToken previous = this.current.previous.pop();
        if (this.current.previous.size() == 0) {
            this.graph.removeVertex(this.current);
        }
        this.current = previous;
        return true;
    }

    public boolean accept(Object token) {

        if (!current.scope.isAdmitted(token, this)) {
            this.error = current.scope.getErrorAndReset();
            return false;
        }

        /**
         * If we get here, the token is accepted and we just have to add a token, link it and make
         * it current, and adjust the undo stack.
         */
        SemanticToken added = new SemanticToken(this.current);
        SemanticLink link = new SemanticLink();

        if (token instanceof Concept) {

            // TODO set concept and redefine constraints
            if (((Concept) token).is(SemanticType.OBSERVABLE)) {
                link.observableRole = SemanticRole.OBSERVABLE;
            } else if (((Concept) token).is(SemanticType.ROLE)) {
                link.observableRole = SemanticRole.ROLE;
            } else if (((Concept) token).is(SemanticType.PREDICATE)) {
                link.observableRole = SemanticRole.TRAIT;
            }
            added.concept = (Concept) token;
            added.scope = SemanticScope.scope(added.concept, this);

        } else if (token instanceof ValueOperator) {

            link.valueOperator = (ValueOperator) token;
            added.scope = SemanticScope.scope((ValueOperator) token, this);

        } else if (token instanceof UnarySemanticOperator) {

            link.unarySemanticOperator = (UnarySemanticOperator) token;
            added.scope = SemanticScope.scope((UnarySemanticOperator) token, this);

        } else if (token instanceof BinarySemanticOperator) {

            link.binarySemanticOperator = (BinarySemanticOperator) token;
            added.scope = SemanticScope.scope((BinarySemanticOperator) token, this);

        } else if (token instanceof SemanticLexicalElement) {

            link.semanticModifier = (SemanticLexicalElement) token;
            added.scope = SemanticScope.scope((SemanticLexicalElement) token, this);

        } else if (token instanceof Unit) {

            // This case is for API use, won't be called from the session builder as units arrive as
            // strings
            link.syntacticElement = "in";
            added.unit = (Unit) token;
            // TODO validate unit

        } else if (token instanceof Currency) {

            // This case is for API use, won't be called from the session builder as units arrive as
            // strings
            link.syntacticElement = "in";
            added.currency = (Currency) token;
            // TODO validate currency

        } else if (token instanceof String) {

            if ("(".equals(token)) {

                link.observableRole = SemanticRole.GROUP_OPEN;
                added.scope = new SemanticScope();

                /*
                 * TODO if under a WHERE, must have a quality and optional operator. This allows too
                 * much.
                 */

                added.scope.lexicalRealm.addAll(current.scope.getAdmittedLexicalInput());
                added.scope.lexicalRealm.remove(SemanticRole.GROUP_OPEN);
                added.scope.lexicalRealm.add(SemanticRole.GROUP_CLOSE);
                added.scope.logicalRealm.addAll(current.scope.getAdmittedLogicalInput());
                // open groups are always for observables, which are specified in the original
                // scope, so add predicates
                added.scope.logicalRealm.add(Constraint.of(SemanticType.PREDICATE));

            } else if (")".equals(token)) {

                /*
                 * find the parent, push the current to the undo stack, revise the scope and throw
                 * away the local node. No new links are made.
                 */
                SemanticToken parent = this.current.getGroupParent();
                if (parent != null) {
                    parent.scope = SemanticScope.scope(parent.buildConcept(), this);
                }
                parent.previous.push(this.current);
                this.current = parent;

                System.out.println(this);

                return true;

            } else {
                // TODO according to context, it may be a unit or a currency, to be validated before
                // acceptance. This should be done in the session, not here.
            }

        } else {
            throw new KlabIllegalStateException("internal: semantic token was accepted but is not handled: " + token);
        }

        graph.addVertex(added);
        graph.addEdge(this.current, added, link);
        this.current = added;

        System.out.println(this);

        return true;
    }

    public SemanticToken getHead() {
        return head;
    }

    /**
     * Return all the components already defined within the boundaries of the observable being defined in the current
     * lexical context.
     *
     * @return
     */
    public Set<SemanticRole> getRoles() {
        Set<SemanticRole> ret = EnumSet.noneOf(SemanticRole.class);
        // TODO
        return ret;
    }

    /**
     * Return all the components already defined within the boundaries of the observable being defined in the current
     * lexical context.
     *
     * @return
     */
    public Set<Object> collect(SemanticRole role) {
        Set<Object> ret = new HashSet<>();
        SemanticToken start = getCurrentLexicalContext();
        // TODO
        return ret;
    }

    /**
     * Return the base traits already specified within the boundaries of the observable being defined in the current
     * lexical context.
     *
     * @return
     */
    public Set<Concept> getBaseTraits() {
        Set<Concept> ret = new HashSet<>();
        return ret;
    }

    /**
     * Return the root token for the current lexical context - either root or the innermost group reachable from
     * current.
     *
     * @return
     */
    public SemanticToken getCurrentLexicalContext() {

        SemanticToken ret = head;
        /*
         * it's always just one incoming, or zero for head, so no need to break anything. The
         * beginning of a new observable is always an empty token.
         */
        for (SemanticLink link : graph.incomingEdgesOf(current)) {
            if (graph.getEdgeSource(link).isEmpty()) {
                ret = graph.getEdgeSource(link);
            }
        }
        return ret;
    }

    public List<StyledKimToken> getStyledCode() {

        List<StyledKimToken> ret = new ArrayList<>();
        collectStyledCode(head, ret);
        return ret;

    }

    private int collectStyledCode(SemanticToken token, List<StyledKimToken> tokens) {

        int n = tokens.size();

        if (token.concept != null) {
            tokens.add(StyledKimToken.create(token.concept));
        } else if (token.unit != null) {
            tokens.add(StyledKimToken.create(token.unit));
        } else if (token.currency != null) {
            tokens.add(StyledKimToken.create(token.currency));
        } else if (token.value != null) {
            tokens.add(StyledKimToken.create(token.value));
        }

        List<SemanticLink> roles = new ArrayList<>();
        List<SemanticLink> traits = new ArrayList<>();
        // this will never be +1 but OK
        List<SemanticLink> observables = new ArrayList<>();

        for (SemanticLink link : graph.outgoingEdgesOf(token)) {
            if (link.observableRole == SemanticRole.TRAIT) {
                traits.add(link);
            } else if (link.observableRole == SemanticRole.ROLE) {
                roles.add(link);
            } else if (link.observableRole == SemanticRole.OBSERVABLE) {
                observables.add(link);
            }
        }

        for (SemanticLink link : traits) {
            collectStyledCode(graph.getEdgeTarget(link), tokens);
        }
        for (SemanticLink link : roles) {
            collectStyledCode(graph.getEdgeTarget(link), tokens);
        }
        for (SemanticLink link : observables) {
            collectStyledCode(graph.getEdgeTarget(link), tokens);
        }

        for (SemanticLink link : graph.outgoingEdgesOf(token)) {

            if (link.valueOperator != null) {

                tokens.add(StyledKimToken.create(link.valueOperator));
                if (collectStyledCode(graph.getEdgeTarget(link), tokens) == 0) {
                    tokens.add(StyledKimToken.unknown());
                }

            } else if (link.binarySemanticOperator != null) {

                tokens.add(StyledKimToken.create(link.binarySemanticOperator));
                if (collectStyledCode(graph.getEdgeTarget(link), tokens) == 0) {
                    tokens.add(StyledKimToken.unknown());
                }

            } else if (link.semanticModifier != null) {

                tokens.add(StyledKimToken.create(link.semanticModifier));
                if (collectStyledCode(graph.getEdgeTarget(link), tokens) == 0) {
                    tokens.add(StyledKimToken.unknown());
                }

            } else if (link.unarySemanticOperator != null) {

                tokens.add(StyledKimToken.create(link.unarySemanticOperator));
                if (collectStyledCode(graph.getEdgeTarget(link), tokens) == 0) {
                    tokens.add(StyledKimToken.unknown());
                }

            } else if (link.syntacticElement != null) {

                tokens.add(StyledKimToken.create(link.syntacticElement));
                if (collectStyledCode(graph.getEdgeTarget(link), tokens) == 0) {
                    tokens.add(StyledKimToken.unknown());
                }

            } else if (link.observableRole == SemanticRole.GROUP_OPEN) {
                tokens.add(StyledKimToken.create("("));
                if (collectStyledCode(graph.getEdgeTarget(link), tokens) == 0) {
                    tokens.add(StyledKimToken.unknown());
                }
                tokens.add(StyledKimToken.create(")"));
            }
        }

        return tokens.size() - n;
    }

    public Collection<String> getErrors() {
        return this.error == null ? Collections.emptyList() : Collections.singleton(this.error);
    }

    public SemanticType getObservableType() {
        Concept concept = buildConcept();
        if (concept != null) {
            return SemanticType.fundamentalType(concept.getType());
        }
        return null;
    }

    /**
     * User data for interactive tracking of contexts, match proposals etc.
     *
     * @return
     */
    public <T> T getData(String key, Class<T> cls) {
        return Utils.Data.asType(data.get(key), cls);
    }

    public void setData(String key, Object value) {
        this.data.put(key, value);
    }

    public static SemanticExpression create(Scope scope) {
        return new SemanticExpression(scope);
    }

    public String dump() {
        return dump(head, 0);
    }

    private String dump(SemanticToken token, int level) {

        String ret = "";
        String spacer = Utils.Strings.spaces(level);
        ret += spacer + token + "\n";
        for (SemanticLink link : graph.outgoingEdgesOf(token)) {
            ret += spacer + "  \u2192 " + link + ":\n";
            ret += dump(graph.getEdgeTarget(link), level + 4);
        }
        return ret;
    }

    @Override
    public String toString() {
        return dump(head, 0);
    }

    public Observable buildObservable() {
        return head.buildObservable();
    }

    public Concept buildConcept() {
        return head.buildConcept();
    }

    public String buildDeclaration() {
        return head.buildDeclaration();
    }
}
