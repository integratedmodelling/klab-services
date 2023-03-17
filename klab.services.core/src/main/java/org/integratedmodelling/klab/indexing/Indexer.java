package org.integratedmodelling.klab.indexing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.ControlledRealTimeReopenThread;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.integratedmodelling.kim.api.IKimAcknowledgement;
import org.integratedmodelling.kim.api.IKimConcept;
import org.integratedmodelling.kim.api.IKimConcept.ObservableRole;
import org.integratedmodelling.kim.api.IKimConceptStatement;
import org.integratedmodelling.kim.api.IKimModel;
import org.integratedmodelling.kim.api.IKimNamespace;
import org.integratedmodelling.kim.api.IKimScope;
import org.integratedmodelling.kim.api.IKimStatement;
import org.integratedmodelling.klab.Urn;
import org.integratedmodelling.klab.api.data.IResource;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.IConcept;
import org.integratedmodelling.klab.api.knowledge.SemanticRole;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.SemanticLexicalElement;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.lang.kim.KimAcknowledgement;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimModelStatement;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimScope;
import org.integratedmodelling.klab.api.lang.kim.KimStatement;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.configuration.Services;
import org.integratedmodelling.klab.exceptions.KlabIOException;
import org.integratedmodelling.klab.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.exceptions.KlabValidationException;
import org.integratedmodelling.klab.indexing.SemanticScope.Constraint;
import org.integratedmodelling.klab.logging.Logging;
import org.integratedmodelling.klab.utils.NumberUtils;

public enum Indexer {

    INSTANCE;

    private Directory index;
    private IndexWriter writer;
    private StandardAnalyzer analyzer;
    private ReferenceManager<IndexSearcher> searcherManager;
    private ControlledRealTimeReopenThread<IndexSearcher> nrtReopenThread;
    // private QueryParser namespaceRemover;

    public static final int MAX_RESULT_COUNT = 9;

    private Indexer() {
        try {
            this.index = new ByteBuffersDirectory(); // new
                                             // MMapDirectory(Configuration.INSTANCE.getDataPath("index").toPath());
            this.analyzer = new StandardAnalyzer();
            IndexWriterConfig config = new IndexWriterConfig(this.analyzer);
            this.writer = new IndexWriter(index, config);

            this.searcherManager = new SearcherManager(writer, true, true, null);
            /**
             * Thread supporting near-realtime index background refresh
             */
            nrtReopenThread = new ControlledRealTimeReopenThread<IndexSearcher>(writer, searcherManager, 1.0, 0.1);
            nrtReopenThread.setName("NRT Reopen Thread");
            nrtReopenThread.setPriority(Math.min(Thread.currentThread().getPriority() + 2, Thread.MAX_PRIORITY));
            nrtReopenThread.setDaemon(true);
            nrtReopenThread.start();

        } catch (IOException e) {
            throw new KlabIOException(e);
        }
    }

    public void index(IResource resource) {

        try {

            Document document = new Document();

            Urn urn = new Urn(resource.getUrn());

            document.add(new StringField("id", urn.getUrn(), Store.YES));
            document.add(new StringField("namespace", urn.getNamespace(), Store.YES));
            document.add(new StringField("catalog", urn.getCatalog(), Store.YES));
            document.add(new TextField("name", urn.getUrn(), Store.YES));
            for (String key : resource.getMetadata().keySet()) {
                if (Metadata.DC_DESCRIPTION.equals(key)) {
                    document.add(new TextField("description", resource.getMetadata().get(key).toString(), Store.YES));
                } else {
                    document.add(new TextField(key, resource.getMetadata().get(key).toString(), Store.YES));
                }
            }

            this.writer.addDocument(document);
        } catch (IOException e) {
            throw new KlabIOException(e);
        }
    }

    public SearchMatch index(KimStatement object, String namespaceId) {

        SearchMatch ret = null;
        Set<SemanticType> semanticType = null;

        if (object.isErrors()) {
            return null;
        }

        if (object instanceof KimConceptStatement) {

            /*
             * TODO the educational "nothings" in the ontologies are probably useful, although
             * certainly not as suggestions. They could be indexed to provide "negative" suggestions
             * for a smarter bar.
             */
            if (!((KimConceptStatement) object).getType().contains(SemanticType.NOTHING)) {

                ret = new SearchMatch(SearchMatch.Type.CONCEPT, ((KimConceptStatement) object).getType());
                ret.setDescription(((IKimConceptStatement) object).getDocstring());
                ret.setId(namespaceId + ":" + ((IKimConceptStatement) object).getName());
                ret.setName(((IKimConceptStatement) object).getName());

                semanticType = (((KimConceptStatement) object).getType());

                /*
                 * TODO a concept that 'equals' something should index its definition with high
                 * weight; a concept that 'is' something should index its parent's definition with
                 * lower weight
                 */
                if (object instanceof KimStatement) {
                    for (KimScope child : ((KimStatement) object).getChildren()) {
                        if (child instanceof KimConceptStatement) {
                            index((KimConceptStatement) child, namespaceId);
                        }
                    }
                }
            }

        } else if (object instanceof KimModelStatement && ((KimModelStatement) object).isSemantic()) {

            ret = new SearchMatch(SearchMatch.Type.MODEL, ((KimModelStatement) object).getObservables().get(0).getMain().getType());
            ret.setDescription(((KimModelStatement) object).getDocstring());
            ret.setName(((KimModelStatement) object).getName());
            ret.setId(((KimModelStatement) object).getName());
            semanticType = (((KimModelStatement) object).getObservables().get(0).getMain().getType());

        } else if (object instanceof KimAcknowledgement) {

            ret = new SearchMatch(SearchMatch.Type.OBSERVATION, ((KimAcknowledgement) object).getObservable().getMain().getType());
            ret.setDescription(((KimAcknowledgement) object).getDocstring());
            ret.setName(((KimAcknowledgement) object).getName());
            ret.setId(((KimAcknowledgement) object).getName());
            semanticType = (((KimAcknowledgement) object).getObservable().getMain().getType());
        }

        if (ret != null) {

            try {

                Document document = new Document();

                document.add(new StringField("id", ret.getId(), Store.YES));
                document.add(new StringField("namespace", namespaceId, Store.YES));
                document.add(new TextField("name", ret.getName(), Store.YES));
                document.add(new TextField("description", ret.getDescription(), Store.YES));
                for (String key : ret.getIndexableFields().keySet()) {
                    document.add(new TextField(key, ret.getIndexableFields().get(key), Store.YES));
                }

                // index type and concepttype as ints
                document.add(new IntPoint("ctype", SemanticType.fundamentalType(ret.conceptType).ordinal()));
                document.add(new IntPoint("mtype", ret.getMatchType().ordinal()));
                document.add(new IntPoint("abstract", ret.isAbstract() ? 1 : 0));
                // ..store them
                document.add(new StoredField("vctype", SemanticType.fundamentalType(ret.conceptType).ordinal()));
                document.add(new StoredField("vmtype", ret.getMatchType().ordinal()));
                document.add(new StoredField("smtype", encodeType(semanticType)));

                this.writer.addDocument(document);

            } catch (Throwable e) {
                throw new KlabInternalErrorException(e);
            }
        }

        return ret;
    }

    private String encodeType(Set<SemanticType> semanticType) {

        String ret = "";
        if (semanticType != null) {
            for (SemanticType type : semanticType) {
                ret += (ret.isEmpty() ? "" : ",") + type.ordinal();
            }
        }
        return ret;
    }

    public void commitChanges() {
        try {
            this.writer.commit();
        } catch (IOException e) {
            throw new KlabIOException(e);
        }
    }

    public void updateNamespace(KimNamespace namespace) {

        Thread writerThread = new Thread(){

            @Override
            public void run() {
                try {
                    writer.deleteDocuments(new TermQuery(new Term("namespace", namespace.getUrn())));
                    writer.commit();
                    for (KimScope statement : namespace.getChildren()) {
                        if (statement instanceof IKimStatement) {
                            index((KimStatement) statement, namespace.getUrn());
                        }
                    }
                    writer.commit();
                } catch (Exception e) {
                    throw new KlabIOException(e);
                }
            }
        };
        writerThread.start();
    }

    public boolean ensureClosed() {
        nrtReopenThread.close();
        if (this.writer.isOpen()) {
            try {
                this.writer.close();
            } catch (IOException e) {
                Logging.INSTANCE.error(e);
                return false;
            }
        }
        return true;
    }

    public List<SearchMatch> query(String currentTerm, SearchContext searchContext) {
        return query(currentTerm, searchContext, MAX_RESULT_COUNT);
    }

    public Query buildQuery(String currentTerm, Analyzer analyzer) {
        QueryParser parser = new QueryParser("name", analyzer);
        // parser.setAllowLeadingWildcard(true);
        try {
            // hai voglia
            return parser.parse("name:" + currentTerm + "*");
        } catch (ParseException e) {
            throw new KlabValidationException(e);
        }
    }

    /**
     * Updated query function: arguments beyond the search term defines the type of result we may be
     * interested in. We will return matches in order of request, incrementally filtering any
     * duplicates.
     * <p>
     * The arguments may be values of the {@link ObservableRole} enum (which specify operators,
     * types or roles) or {@link IKimConcept.Type}s for concept categories, or collections thereof.
     * The may also be filters for properties to apply to concepts.
     * 
     * @param term
     * @param where
     * @return
     */
    public List<SearchMatch> query(String term, SemanticScope composer, int maxResults) {

        List<SearchMatch> ret = new ArrayList<>();

        if (maxResults <= 0) {
            maxResults = MAX_RESULT_COUNT;
        }

        for (SemanticRole role : composer.getAdmittedLexicalInput()) {
            if (role.kimDeclaration.isEmpty() || role.kimDeclaration.startsWith(term)) {
                switch(role) {
                case ADJACENT:
                    ret.add(new SearchMatch(SemanticLexicalElement.ADJACENT_TO));
                    break;
                case CAUSANT:
                    ret.add(new SearchMatch(SemanticLexicalElement.CAUSING));
                    break;
                case CAUSED:
                    ret.add(new SearchMatch(SemanticLexicalElement.CAUSED_BY));
                    break;
                case COMPRESENT:
                    ret.add(new SearchMatch(SemanticLexicalElement.WITH));
                    break;
                case CONTEXT:
                    ret.add(new SearchMatch(SemanticLexicalElement.WITHIN));
                    break;
                case COOCCURRENT:
                    ret.add(new SearchMatch(SemanticLexicalElement.DURING));
                    break;
                case INHERENT:
                    ret.add(new SearchMatch(SemanticLexicalElement.OF));
                    break;
                case RELATIONSHIP_SOURCE:
                    ret.add(new SearchMatch(SemanticLexicalElement.LINKING));
                    break;
                case RELATIONSHIP_TARGET:
                    ret.add(new SearchMatch(SemanticLexicalElement.TO));
                    break;
                case GOAL:
                    ret.add(new SearchMatch(SemanticLexicalElement.FOR));
                    break;
                case UNIT:
                case CURRENCY:
                case INLINE_VALUE:
                case GROUP_OPEN:
                case DISTRIBUTED_UNIT:
                	SearchMatch match = new SearchMatch(role);
                	// space match gets suggestions in these cases
                	if (term.isEmpty() || match.getId().startsWith(term)) {
                		ret.add(match);
                	}
                    break;
                case LOGICAL_OPERATOR:
                    break;
                case UNARY_OPERATOR:
                    ret.addAll(matchUnaryOperators(term));
                    break;
                case VALUE_OPERATOR:
                    ret.addAll(matchValueOperators(term));
                    break;
                default:
                    break;
                }
            }
        }

        if (composer.getAdmittedLogicalInput().size() > 0) {

            IndexSearcher searcher;
            try {
                searcher = searcherManager.acquire();
            } catch (IOException e) {
                // adorable exception management
                throw new KlabIOException(e);
            }

            Set<String> ids = new HashSet<>();
            try {

                TopDocs docs = searcher.search(buildQuery(term, this.analyzer), maxResults);
                ScoreDoc[] hits = docs.scoreDocs;

                for (ScoreDoc hit : hits) {

                    Document document = searcher.doc(hit.doc);
                    Concept concept = Services.INSTANCE.getReasoner().resolveConcept(document.get("id"));
                    SearchMatch.Type matchType = SearchMatch.Type.values()[Integer.parseInt(document.get("vmtype"))];

                    if (concept == null || ids.contains(document.get("id"))) {
                        continue;
                    }

                    for (SemanticScope.Constraint constraint : composer.getAdmittedLogicalInput()) {

                        if (constraint.matches(concept)) {

                            SearchMatch match = new SearchMatch();
                            match.setId(document.get("id"));
                            match.setName(document.get("name"));
                            match.setDescription(document.get("description"));
                            match.setScore(hit.score);
                            match.setSemantics(decodeType(document.get("smtype")));
                            match.setMatchType(matchType);
                            match.getConceptType().add(SemanticType.values()[Integer.parseInt(document.get("vctype"))]);

                            ret.add(match);
                            ids.add(document.get("id"));

                            break;
                        }
                    }
                }

            } catch (Exception e) {
                throw new KlabIOException(e);
            } finally {
                try {
                    searcherManager.release(searcher);
                } catch (IOException e) {
                    // fucking unbelievable, they want it in finally and make it throw a checked
                    // exception
                    throw new KlabIOException(e);
                }
            }
        }

        return ret;
    }

    Collection<SearchMatch> matchObservableModifier(String term, ObservableRole role) {
        List<SearchMatch> ret = new ArrayList<>();
        return ret;
    }

    Collection<SearchMatch> matchValueOperators(String term) {
        List<SearchMatch> ret = new ArrayList<>();
        for (ValueOperator op : ValueOperator.values()) {
            if (op.name().toLowerCase().startsWith(term)) {
                ret.add(new SearchMatch(op));
            }
        }
        return ret;
    }

    Collection<SearchMatch> matchUnaryOperators(String term) {
        List<SearchMatch> ret = new ArrayList<>();
        for (UnarySemanticOperator op : UnarySemanticOperator.values()) {
            if (op.declaration[0].startsWith(term)) {
                ret.add(new SearchMatch(op));
            }
        }
        return ret;
    }

    public List<SearchMatch> query(String currentTerm, SearchContext context, int maxResults) {

        List<SearchMatch> ret = new ArrayList<>();

        for (SearchContext.Constraint constraint : context.getConstraints()) {

            List<SearchMatch> cret = new ArrayList<>();

            // FIXME this is to avoid duplications, which should not be necessary if this
            // whole thing
            // could take a month.
            Set<String> ids = new HashSet<>();

            if (constraint.isMatcher()) {
                for (SearchMatch match : constraint.getMatches(currentTerm)) {
                    if (!ids.contains(match.getId())) {
                        cret.add(match);
                        ids.add(match.getId());
                    }
                }
            }

            if (constraint.isQuery()) {

                IndexSearcher searcher;
                try {
                    searcher = searcherManager.acquire();
                } catch (IOException e) {
                    // adorable exception management
                    throw new KlabIOException(e);
                }

                try {
                    TopDocs docs = searcher.search(constraint.buildQuery(currentTerm, this.analyzer), maxResults);
                    ScoreDoc[] hits = docs.scoreDocs;

                    for (ScoreDoc hit : hits) {

                        Document document = searcher.doc(hit.doc);

                        SearchMatch.Type matchType = SearchMatch.Type.values()[Integer.parseInt(document.get("vmtype"))];

                        if (constraint.getType() == matchType && !ids.contains(document.get("id"))) {

                            SearchMatch match = new SearchMatch();
                            match.setId(document.get("id"));
                            match.setName(document.get("name"));
                            match.setDescription(document.get("description"));
                            match.setScore(hit.score);
                            match.setSemantics(decodeType(document.get("smtype")));
                            match.setMatchType(matchType);
                            match.getConceptType().add(SemanticType.values()[Integer.parseInt(document.get("vctype"))]);

                            cret.add(match);
                            ids.add(document.get("id"));
                        }
                    }

                } catch (Exception e) {
                    throw new KlabIOException(e);
                } finally {
                    try {
                        searcherManager.release(searcher);
                    } catch (IOException e) {
                        // fucking unbelievable, they want it in finally and make it throw a checked
                        // exception
                        throw new KlabIOException(e);
                    }
                }
            }

            /*
             * filter matches if the constraint requires it.
             */
            if (constraint.isFilter()) {
                List<SearchMatch> fret = new ArrayList<>();
                for (SearchMatch match : cret) {
                    if (constraint.filter(match)) {
                        fret.add(match);
                    }
                }
                cret = fret;
            }
            ret.addAll(cret);
        }

        return ret;
    }

    private Set<SemanticType> decodeType(String string) {
        Set<SemanticType> ret = EnumSet.noneOf(SemanticType.class);
        for (int n : Utils.Numbers.intArrayFromString(string)) {
            ret.add(SemanticType.values()[n]);
        }
        return ret;
    }
}
