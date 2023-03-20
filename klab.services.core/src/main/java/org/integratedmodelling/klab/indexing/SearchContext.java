package org.integratedmodelling.klab.indexing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.integratedmodelling.klab.api.exceptions.KValidationException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.BinarySemanticOperator;
import org.integratedmodelling.klab.api.lang.SemanticLexicalElement;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticMatch;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticMatch.TokenClass;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.configuration.Services;

import com.google.common.collect.Sets;

/**
 * 
 * @author Ferd
 *
 */
public class SearchContext {

    /*
     * we send this one up the chain of accepted matches to collect the meaning so far.
     */
    public class Meaning {

        Set<SemanticType> semantics;
        Concept observable;
        Set<Concept> traits = new HashSet<>();
        Set<Concept> roles = new HashSet<>();
        boolean boundary;

        /**
         * True when we hit a semantic boundary, such as an infix operator or a closed parenthesis.
         * 
         * @return
         */
        boolean isBoundary() {
            return boundary;
        }

        public boolean is(SemanticType type) {
            return semantics == null ? false : semantics.contains(type);
        }

        void accept(SemanticMatch match) {

            if (match != null) {
                switch(match.getMatchType()) {
                case CONCEPT:
                case PRESET_OBSERVABLE:
                    Concept concept = Services.INSTANCE.getReasoner().resolveObservable(match.getId()).getSemantics();
                    if (concept != null) {
                        if (concept.is(SemanticType.OBSERVABLE)) {
                            this.observable = concept;
                            this.semantics = EnumSet.copyOf(concept.getType());
                        } else if (concept.is(SemanticType.TRAIT)) {
                            this.traits.add(concept);
                        } else if (concept.is(SemanticType.ROLE)) {
                            this.roles.add(concept);
                        }
                    }
                    break;
                case CLOSED_PARENTHESIS:
                case BINARY_OPERATOR:
                    this.boundary = true;
                    break;
                case UNARY_OPERATOR:
                    Set<SemanticType> type = match.getUnaryOperator().getApplicableType(null);
                    // we don't have an observable
                    type.remove(SemanticType.OBSERVABLE);
                    if (type != null) {
                        this.semantics = type;
                    }
                    break;
                case MODEL:
                    break;
                case MODIFIER:
                    break;
                case OBSERVATION:
                    break;
                case SEPARATOR:
                case OPEN_PARENTHESIS:
                    // won't happen
                    break;
                default:
                    break;

                }
            }
        }

        public Set<SemanticType> getSemantics() {
            return semantics;
        }

        public Concept getObservable() {
            return observable;
        }

        public Collection<Concept> getTraits() {
            return traits;
        }

        public Collection<Concept> getRoles() {
            return roles;
        }

        @Override
        public String toString() {
            String ret = "";
            ret += "Meaning: " + this.semantics + " ";
            ret += "[OBS=" + this.observable + "]";
            ret += "[TRT=" + this.traits + "]";
            ret += "[ROL=" + this.roles + "]";
            return ret;
        }

    }

    // these are in OR - anything matching any of these is acceptable. No
    // constraints means everything is acceptable.
    private List<Constraint> constraints = new ArrayList<>();
    private Set<SemanticMatch.Type> constrainttypes = EnumSet.noneOf(SemanticMatch.Type.class);
    // previous in list, containing the preceding meaning.
    private SearchContext previous = null;
    // non-null in children of previous context. Must have parent == null to be
    // complete.
    private SearchContext parent = null;
    private SemanticMatch acceptedMatch;
    private TokenClass nextTokenType = TokenClass.TOKEN;
    private SearchContext childContext;
    private int parenthesisDepth;

    public static SearchContext createNew() {
        SearchContext ret = new SearchContext();
        ret.allow(Constraint.allPrefixOperators());
        ret.allow(Constraint.allObservables(false));
        ret.allow(Constraint.allTraits(false));
        return ret;
    }

    private SearchContext() {
    }

    public void addConstraint(Constraint constraint) {
        allow(constraint);
        constrainttypes.add(constraint.getType());
    }

    private SearchContext(SearchContext parent) {
        this.previous = parent;
    }

    private SearchContext(SearchContext parent, SemanticMatch match) {
        this.previous = parent;
        this.acceptedMatch = match;
    }

    static class Condition {

        SemanticMatch.Type type;
        Set<SemanticType> semantics;
        Concept c1;
        Concept c2;

        public Condition(SemanticMatch.Type type) {
            this.type = type;
        }

        public Condition(SemanticType... semantics) {

            this.semantics = EnumSet.noneOf(SemanticType.class);

            for (SemanticType type : semantics) {
                this.semantics.add(type);
            }
        }

        boolean filter(SemanticMatch document) {
            // TODO
            return true;
        }

        @Override
        public String toString() {
            return "[CONDITION " + type + " " + (semantics == null ? "" : semantics.toString())
                    + (c1 == null ? "" : (" C1: " + c1)) + (c2 == null ? "" : (" C2: " + c2)) + "]";
        }
    }

    /**
     * Constraints can be matchers (which produce matches directly) or queries (which produce
     * queries for documents). Optionally, they may also be filters (which scan the query results
     * and accept/reject them selectively). Conditions of various types can be added to build
     * intelligent filters.
     * 
     * @author ferdinando.villa
     *
     */
    static class Constraint {

        // if not empty, these are in AND and filter is true
        private List<Condition> conditions = new ArrayList<>();
        private static Set<SemanticLexicalElement> allModifiers;

        static {
            allModifiers = new HashSet<>();
            for (SemanticLexicalElement modifier : SemanticLexicalElement.values()) {
                allModifiers.add(modifier);
            }
        }

        boolean filter;
        boolean query;
        boolean matcher;
        Set<SemanticLexicalElement> modifiers = null;
        Set<Concept> baseTraitBlacklist;

        // if set, all matches must have at least minMatches of the types in here
        Set<SemanticType> semantics;
        int minMatches = 1;

        // the type selects some pre-defined matches
        SemanticMatch.Type type;

        // only effective if filtering
        private boolean allowAbstract;

        private Constraint(SemanticMatch.Type type) {
            this.type = type;
        }

        public String toString() {
            String ret = "" + this.type;
            ret += filter ? " FILTER" : "";
            ret += query ? " QUERY" : "";
            ret += matcher
                    ? " MATCH"
                    : "" + (type == SemanticMatch.Type.MODIFIER ? (modifiers == null ? "[ALL]" : modifiers.toString()) : "");
            ret += semantics == null || semantics.isEmpty() ? "" : (" " + semantics);
            ret += " " + conditions;
            return ret;
        }

        SemanticMatch.Type getType() {
            return type;
        }

        /**
         * true if it wants to filter matches once produced by a query (usually when reasoning is
         * required). Adding conditions will set this to true.
         * 
         * @return
         */
        boolean isFilter() {
            return filter;
        }

        /**
         * true if it produces matches directly
         * 
         * @return
         */
        boolean isMatcher() {
            return matcher;
        }

        /**
         * true if it produces a query for the index
         * 
         * @return
         */
        boolean isQuery() {
            return query;
        }

        /**
         * Add a condition
         * 
         * @param condition
         */
        public void addCondition(Condition condition) {
            this.conditions.add(condition);
            this.filter = true;
        }

        /**
         * If {@link #isMatcher()}, this will be called to produce any matches directly. The matches
         * will also be filtered if {@link #isFilter()}.
         * 
         * @param queryTerm
         * @return
         */
        List<SemanticMatch> getMatches(String queryTerm) {
            List<SemanticMatch> ret = new ArrayList<>();
            if (this.type != null) {
                switch(this.type) {
                case BINARY_OPERATOR:
                    for (BinarySemanticOperator op : BinarySemanticOperator.values()) {
                        if (op.name().toLowerCase().startsWith(queryTerm)) {
                            ret.add(new SemanticMatch(op));
                        }
                    }
                    break;
                case MODIFIER:
                    for (SemanticLexicalElement op : (modifiers == null ? allModifiers : modifiers)) {
                        if (op.declaration[0].startsWith(queryTerm)) {
                            ret.add(new SemanticMatch(op));
                        }
                    }
                    break;
                case UNARY_OPERATOR:
                    for (UnarySemanticOperator op : UnarySemanticOperator.values()) {
                        if (op.declaration[0].startsWith(queryTerm)) {
                            ret.add(new SemanticMatch(op));
                        }
                    }
                    break;
                default:
                    break;

                }
            }
            return ret;
        }

        public boolean filter(SemanticMatch match) {

            if (!allowAbstract && match.getSemantics() != null) {
                if (match.getSemantics().contains(SemanticType.ABSTRACT)
                        // abstract qualities can be made concrete by adding inherency - we let them
                        // through and refuse to
                        // observe unless they have 'of' (should put the A in the icon shown in the
                        // UI
                        // until inherency is
                        // there).
                        && !match.getSemantics().contains(SemanticType.QUALITY)
                        // abstract traits are the only ones that are observable in their own right
                        && !match.getSemantics().contains(SemanticType.TRAIT)) {
                    return false;
                }
            }

            if (this.semantics != null) {

                if (match.getSemantics() == null) {
                    return false;
                }

                Set<SemanticType> intersection = Sets.intersection(match.getSemantics(), this.semantics);

                if (minMatches < 0) {
                    if (intersection.size() < this.semantics.size()) {
                        return false;
                    }
                } else if (intersection.size() < minMatches) {
                    return false;
                }
            }

            for (Condition condition : conditions) {
                if (!condition.filter(match)) {
                    return false;
                }
            }
            return true;
        }

        public static Constraint allObservables(boolean allowAbstract) {
            Constraint ret = new Constraint(SemanticMatch.Type.CONCEPT);
            ret.semantics = EnumSet.of(SemanticType.OBSERVABLE);
            ret.query = true;
            ret.filter = true;
            ret.allowAbstract = allowAbstract;
            return ret;
        }

        public static Constraint allTraits(boolean allowAbstract) {
            Constraint ret = new Constraint(SemanticMatch.Type.CONCEPT);
            ret.semantics = EnumSet.of(SemanticType.TRAIT);
            ret.query = true;
            ret.filter = true;
            ret.allowAbstract = allowAbstract;
            return ret;
        }

        public static Constraint otherTraits(Collection<Concept> traits) {
            Constraint ret = new Constraint(SemanticMatch.Type.CONCEPT);
            ret.semantics = EnumSet.of(SemanticType.TRAIT);
            ret.query = true;
            ret.filter = true;
            ret.allowAbstract = true;
            ret.baseTraitBlacklist = new HashSet<>();
            for (Concept trait : traits) {
                Concept base = Services.INSTANCE.getReasoner().baseParentTrait(trait);
                if (base != null) {
                    ret.baseTraitBlacklist.add(base);
                }
            }
            return ret;
        }

        /**
         * Match the types and ensure one or more is in the matched object.
         * 
         * @param types
         * @return
         */
        public static Constraint with(Set<SemanticType> types) {
            Constraint ret = new Constraint(SemanticMatch.Type.CONCEPT);
            ret.semantics = types;
            ret.query = true;
            ret.filter = true;
            return ret;
        }

        /**
         * Call only after with() to remove any of the types already accepted.
         * 
         * @param types
         * @return
         */
        public Constraint without(Set<SemanticType> types) {
            EnumSet<SemanticType> set = EnumSet.copyOf(this.semantics);
            set.removeAll(types);
            this.semantics = set;
            return this;
        }

        public Constraint plus(SemanticType... types) {
            this.semantics.addAll(Utils.Collections.arrayToList(types));
            return this;
        }

        /**
         * Match the types and ensure the match have at least matchCount of them. Pass -1 for all of
         * them.
         * 
         * @param types
         * @param matchCount
         * @return
         */
        public static Constraint with(Set<SemanticType> types, int matchCount) {
            Constraint ret = new Constraint(SemanticMatch.Type.CONCEPT);
            ret.semantics = types;
            ret.query = true;
            ret.filter = true;
            ret.minMatches = matchCount;
            return ret;
        }

        public static Constraint allPrefixOperators() {
            Constraint ret = new Constraint(SemanticMatch.Type.UNARY_OPERATOR);
            ret.matcher = true;
            return ret;
        }

        public Query buildQuery(String currentTerm, Analyzer analyzer) {
            QueryParser parser = new QueryParser("name", analyzer);
            // parser.setAllowLeadingWildcard(true);
            try {
                // hai voglia
                return parser.parse("name:" + currentTerm + "*");
            } catch (ParseException e) {
                throw new KValidationException(e);
            }
        }

        public Constraint applyingTo(SemanticType type) {
            // TODO Auto-generated method stub
            return this;
        }

        /**
         * We get here when we have an observable. TODO there also should be no modifiers within the
         * same scope.
         * 
         * @param semantics
         * @return
         */
        public static Constraint modifiersFor(Set<SemanticType> semantics) {

            Constraint ret = new Constraint(SemanticMatch.Type.MODIFIER);
            ret.matcher = true;
            ret.modifiers = new HashSet<>();
            if (semantics.contains(SemanticType.QUALITY)) {

                ret.modifiers.add(SemanticLexicalElement.BY);
                ret.modifiers.add(SemanticLexicalElement.WHERE);
                ret.modifiers.add(SemanticLexicalElement.IS);
                ret.modifiers.add(SemanticLexicalElement.SAMEAS);
                ret.modifiers.add(SemanticLexicalElement.WITHOUT);

                if (SemanticType.isNumeric(semantics)) {

                    ret.modifiers.add(SemanticLexicalElement.PLUS);
                    ret.modifiers.add(SemanticLexicalElement.TIMES);
                    ret.modifiers.add(SemanticLexicalElement.GREATER);
                    ret.modifiers.add(SemanticLexicalElement.GREATEREQUAL);
                    ret.modifiers.add(SemanticLexicalElement.LESS);
                    ret.modifiers.add(SemanticLexicalElement.LESSEQUAL);
                    ret.modifiers.add(SemanticLexicalElement.MINUS);
                    ret.modifiers.add(SemanticLexicalElement.OVER);

                    if (semantics.contains(SemanticType.EXTENSIVE_PROPERTY)
                            || semantics.contains(SemanticType.INTENSIVE_PROPERTY)) {
                        ret.modifiers.add(SemanticLexicalElement.IN);
                    }

                    if (semantics.contains(SemanticType.NUMEROSITY)) {
                        ret.modifiers.add(SemanticLexicalElement.PER);
                    }

                }

            }
            ret.modifiers.add(SemanticLexicalElement.ADJACENT_TO);
            ret.modifiers.add(SemanticLexicalElement.CAUSED_BY);
            ret.modifiers.add(SemanticLexicalElement.CAUSING);
            ret.modifiers.add(SemanticLexicalElement.CONTAINED_IN);
            ret.modifiers.add(SemanticLexicalElement.CONTAINING);
            ret.modifiers.add(SemanticLexicalElement.DOWN_TO);
            ret.modifiers.add(SemanticLexicalElement.DURING);
            ret.modifiers.add(SemanticLexicalElement.FOR);
            ret.modifiers.add(SemanticLexicalElement.OF);
            ret.modifiers.add(SemanticLexicalElement.WITH);
            ret.modifiers.add(SemanticLexicalElement.WITHIN);

            return ret;
        }

        public Constraint applicableTo(Collection<Concept> collectTraits) {
            // TODO Auto-generated method stub
            return this;
        }

        // Unit or currency
        public static Constraint unit(Set<SemanticType> semantics) {
            return null;
        }

        // public static Constraint with(SemanticType type) {
        // return with(EnumSet.of(type));
        // }

        public static Constraint with(SemanticType... types) {
            return with(EnumSet.copyOf(Utils.Collections.arrayToList(types)));
        }

        public Constraint applyingTo(Set<SemanticType> semantics) {
            // TODO Auto-generated method stub
            return this;
        }

        /**
         * Literal value matching the passed semantics. Either a concept or a literal.
         * 
         * @param semantics
         * @return
         */
        public static Constraint value(Set<SemanticType> semantics) {
            // TODO Auto-generated method stub
            return null;
        }

    }

    public SearchContext accept(SemanticMatch match) {

        switch(nextTokenType) {
        case BOOLEAN:
            break;
        case DOUBLE:
            break;
        case INTEGER:
            break;
        case TEXT:
            break;
        case TOKEN:
            return acceptToken(match);
        case CURRENCY:
        case UNIT:
            break;
        }

        return null;

    }

    public void allow(Constraint constraint) {
        constraints.add(constraint);
    }

    private SearchContext acceptToken(SemanticMatch match) {

        /*
         * Accepts it
         */
        SearchContext ret = new SearchContext(this, match);

        /*
         * Meaning BEFORE acceptance
         */
        Meaning meaning = collectMeaning();

        // System.out.println("Meaning before accepting: " + meaning);

        if (match.getUnaryOperator() != null) {
            ret.allow(Constraint.with(match.getUnaryOperator().getAllowedOperandTypes()));
            switch(match.getUnaryOperator()) {
            case COUNT:
                ret.allow(Constraint.allTraits(false).applyingTo(SemanticType.COUNTABLE));
                break;
            case DISTANCE:
                ret.allow(Constraint.allTraits(false).applyingTo(SemanticType.DISTANCE));
                break;
            case MAGNITUDE:
            case LEVEL:
                ret.allow(Constraint.allTraits(false).applyingTo(SemanticType.QUALITY));
                break;
            case NOT:
                ret.allow(Constraint.allTraits(false).applyingTo(SemanticType.ATTRIBUTE).applyingTo(SemanticType.DENIABLE));
                break;
            case OCCURRENCE:
                ret.allow(Constraint.with(SemanticType.COUNTABLE));
                ret.allow(Constraint.allTraits(false).applyingTo(SemanticType.COUNTABLE));
                break;
            case PRESENCE:
                ret.allow(Constraint.with(SemanticType.COUNTABLE));
                ret.allow(Constraint.allTraits(false).applyingTo(SemanticType.COUNTABLE));
                break;
            case PROBABILITY:
                ret.allow(Constraint.with(SemanticType.EVENT));
                ret.allow(Constraint.allTraits(false).applyingTo(SemanticType.EVENT));
                break;
            case PROPORTION:
                // TODO also quality? Then need to allow the second piece (in)
                ret.allow(Constraint.with(SemanticType.TRAIT));
                ret.allow(Constraint.allTraits(false).applyingTo(SemanticType.TRAIT));
                break;
            case RATIO:
                // TODO second piece (over)
                ret.allow(Constraint.with(SemanticType.QUALITY));
                ret.allow(Constraint.allTraits(false).applyingTo(SemanticType.QUALITY));
                break;
            case TYPE:
                ret.allow(Constraint.with(SemanticType.COUNTABLE));
                ret.allow(Constraint.allTraits(false).applyingTo(SemanticType.OBSERVABLE));
                break;
            case UNCERTAINTY:
                ret.allow(Constraint.with(SemanticType.QUALITY));
                ret.allow(Constraint.allTraits(false).applyingTo(SemanticType.QUALITY));
                break;
            case VALUE:
                ret.allow(Constraint.with(SemanticType.OBSERVABLE));
                ret.allow(Constraint.allTraits(false).applyingTo(SemanticType.OBSERVABLE));
                break;
            default:
                break;

            }
        } else if (match.getBinaryOperator() != null) {

            switch(match.getBinaryOperator()) {
            case FOLLOWS:
                ret.allow(Constraint.with(SemanticType.EVENT));
                ret.allow(Constraint.allTraits(false).applyingTo(SemanticType.EVENT));
                break;
            case INTERSECTION:
            case UNION:
                ret.allow(Constraint.with(meaning.getSemantics()));
                ret.allow(Constraint.allTraits(false).applyingTo(meaning.getSemantics()));
                break;
            // case BY:
            // break;
            default:
                break;
            }

        } else if (match.getModifier() != null) {

            switch(match.getModifier()) {
            case ADJACENT_TO:
                ret.allow(Constraint.with(SemanticType.COUNTABLE));
                ret.allow(Constraint.allTraits(false)/* .applyingTo(SemanticType.EVENT) */);
                break;
            case BY:
                ret.allow(Constraint.with(SemanticType.QUALITY_TYPES).plus(SemanticType.COUNTABLE, SemanticType.TRAIT)
                        .without(SemanticType.CONTINUOUS_QUALITY_TYPES));
                // ret.allow(Constraint.allTraits(false)/*
                // .applyingTo(SemanticType.EVENT) */);
                break;
            case CAUSED_BY:
                ret.allow(Constraint.with(SemanticType.PROCESS, SemanticType.EVENT));
                ret.allow(Constraint.allTraits(false)/* .applyingTo(SemanticType.EVENT) */);
                break;
            case CAUSING:
                ret.allow(Constraint.with(SemanticType.PROCESS, SemanticType.EVENT));
                ret.allow(Constraint.allTraits(false)/* .applyingTo(SemanticType.EVENT) */);
                break;
            case CONTAINED_IN:
                ret.allow(Constraint.with(SemanticType.COUNTABLE));
                ret.allow(Constraint.allTraits(false)/* .applyingTo(SemanticType.EVENT) */);
                break;
            case CONTAINING:
                ret.allow(Constraint.with(SemanticType.COUNTABLE));
                ret.allow(Constraint.allTraits(false)/* .applyingTo(SemanticType.EVENT) */);
                break;
            case DOWN_TO:
                ret.allow(Constraint.with(SemanticType.CLASS, SemanticType.TRAIT));
                ret.allow(Constraint.allTraits(false)/* .applyingTo(SemanticType.EVENT) */);
                break;
            case DURING:
                ret.allow(Constraint.with(SemanticType.EVENT));
                ret.allow(Constraint.allTraits(false)/* .applyingTo(SemanticType.EVENT) */);
                break;
            case FOR:
                ret.allow(Constraint.with(SemanticType.COUNTABLE));
                ret.allow(Constraint.allTraits(false)/* .applyingTo(SemanticType.EVENT) */);
                break;
            case IN:
                ret.nextTokenType = meaning.getSemantics().contains(SemanticType.MONEY)
                        || meaning.getSemantics().contains(SemanticType.MONETARY_VALUE) ? TokenClass.CURRENCY : TokenClass.UNIT;
                ret.allow(Constraint.unit(meaning.getSemantics()));
                break;
            case OF:
                ret.allow(Constraint.with(SemanticType.COUNTABLE));
                ret.allow(Constraint.allTraits(false).applyingTo(SemanticType.COUNTABLE));
                break;
            case PER:
                ret.nextTokenType = TokenClass.UNIT;
                ret.allow(Constraint.unit(meaning.getSemantics()));
                break;
            case WITH:
                ret.allow(Constraint.with(SemanticType.OBSERVABLE));
                ret.allow(Constraint.allTraits(false));
                break;
            case WITHIN:
                ret.allow(Constraint.with(SemanticType.COUNTABLE));
                ret.allow(Constraint.allTraits(false).applyingTo(SemanticType.COUNTABLE));
                break;
            case GREATER:
                ret.allow(Constraint.with(SemanticType.CONTINUOUS_QUALITY_TYPES));
                break;
            case GREATEREQUAL:
                ret.allow(Constraint.with(SemanticType.CONTINUOUS_QUALITY_TYPES));
                break;
            case IS:
                if (SemanticType.isNumeric(meaning.getSemantics())) {
                    ret.nextTokenType = TokenClass.DOUBLE;
                }
                break;
            case LESS:
                ret.nextTokenType = TokenClass.DOUBLE;
                ret.allow(Constraint.with(SemanticType.CONTINUOUS_QUALITY_TYPES));
                break;
            case LESSEQUAL:
                ret.nextTokenType = TokenClass.DOUBLE;
                ret.allow(Constraint.with(SemanticType.CONTINUOUS_QUALITY_TYPES));
                break;
            case MINUS:
                ret.nextTokenType = TokenClass.DOUBLE;
                ret.allow(Constraint.with(SemanticType.CONTINUOUS_QUALITY_TYPES));
                break;
            case OVER:
                ret.nextTokenType = TokenClass.DOUBLE;
                ret.allow(Constraint.with(SemanticType.CONTINUOUS_QUALITY_TYPES));
                break;
            case PLUS:
                ret.nextTokenType = TokenClass.DOUBLE;
                ret.allow(Constraint.with(SemanticType.CONTINUOUS_QUALITY_TYPES));
                break;
            case SAMEAS:
                if (SemanticType.isNumeric(meaning.getSemantics())) {
                    ret.nextTokenType = TokenClass.DOUBLE;
                }
                ret.allow(Constraint.with(meaning.getSemantics()));
                ret.allow(Constraint.value(meaning.getSemantics()));
                break;
            case TIMES:
                ret.nextTokenType = TokenClass.DOUBLE;
                break;
            case WHERE:
                break;
            case WITHOUT:
                if (SemanticType.isNumeric(meaning.getSemantics())) {
                    ret.nextTokenType = TokenClass.DOUBLE;
                } else {
                    ret.allow(Constraint.allTraits(false));
                    // TODO must reconstruct the semantics so far and pass it as this
                    // ret.allow(Constraint.traitsIncarnating(getAcceptedSemantics()));
                }
                break;
            default:
                break;
            }

        } else {

            /*
             * Convert to meaning AFTER acceptance
             */
            meaning = ret.collectMeaning();

            // if we have an observable, no more observables
            if (meaning.getObservable() != null) {
                ret.allow(Constraint.modifiersFor(meaning.getSemantics()));
            } else {
                // add traits with another base trait
                ret.allow(Constraint.otherTraits(meaning.getTraits()).applyingTo(meaning.getSemantics()));
                ret.allow(Constraint.allObservables(false).applicableTo(meaning.getTraits()));
            }

            /*
             * TRAIT constraint: we can put this in the else above, which will cause traits in the
             * front only and inhibit trait definition after the observable is set. Probably good
             * design discipline although it may be annoying.
             */

        }

        // System.out.println("Meaning after accepting: " + ret.collectMeaning());

        return ret;
    }

    // private Set<SemanticType> getSemantics() {
    // return collectMeaning().semantics;
    // }

    public boolean isAllowed(SemanticMatch.Type type) {
        return constrainttypes.isEmpty() || constrainttypes.contains(type);
    }

    public boolean isEnd() {
        return false;
    }

    public boolean isConsistent() {
        return false;
    }

    public String getUrn() {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean isEmpty() {
        // root context is empty
        return previous == null;
    }

    public SearchContext previous() {
        return previous;
    }

    public static SearchContext createNew(Set<SemanticMatch.Type> matchTypes, Set<SemanticType> semanticTypes) {
        SearchContext ret = new SearchContext();
        if (matchTypes.isEmpty() && semanticTypes.isEmpty()) {
            // first context can select operators, non-abstract traits or non-abstract
            // observables
            ret.allow(Constraint.allPrefixOperators());
            ret.allow(Constraint.allObservables(false));
            ret.allow(Constraint.allTraits(false));
        } else {
            // TODO
        }
        return ret;
    }

    public List<Constraint> getConstraints() {
        return constraints;
    }

    public String dump() {
        return dump(0);
    }

    private String dump(int offset) {
        String prefix = Utils.Strings.spaces(offset);
        String ret = prefix + "CTYPES: " + constrainttypes + "\n";
        if (acceptedMatch != null) {
            ret += prefix + "ACCEPTED: " + acceptedMatch + "\n";
        }
        if (constraints != null && !constraints.isEmpty()) {
            ret += prefix + "CONSTRAINTS:\n";
            for (Constraint c : constraints) {
                ret += prefix + "  " + c + "\n";
            }
        }
        if (previous != null) {
            previous.dump(offset + 3);
        }
        return ret;
    }

    public TokenClass getNextTokenType() {
        return nextTokenType;
    }

    public void setNextTokenType(TokenClass nextTokenType) {
        this.nextTokenType = nextTokenType;
    }

    public boolean isComposite() {
        return childContext != null;
    }

    public SearchContext getChildContext() {
        return childContext;
    }

    public Meaning collectMeaning() {
        Meaning meaning = new Meaning();
        SearchContext current = this;
        while(current != null && !meaning.isBoundary()) {
            meaning.accept(current.acceptedMatch);
            current = current.previous;
        }
        return meaning;
    }

    public SemanticMatch getAcceptedMatch() {
        return acceptedMatch;
    }

    public int getDepth() {
        return parenthesisDepth;
    }

}
