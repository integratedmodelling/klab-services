package org.integratedmodelling.klab.indexing;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ControlledRealTimeReopenThread;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticMatch;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;

/** Embedded, near-real-time full-text index for the resource catalog. */
public class ResourceIndexer {

  public static final int MAX_RESULT_COUNT = 20;
  private static final String SCHEMA_VERSION = "2";
  private static final String SCHEMA_VERSION_KEY = "resource-index-schema";

  private final Directory index;
  private final IndexWriter writer;
  private final StandardAnalyzer analyzer;
  private final ReferenceManager<IndexSearcher> searcherManager;
  private final ControlledRealTimeReopenThread<IndexSearcher> nrtReopenThread;

  public static ResourceIndexer create() {
    return new ResourceIndexer(null);
  }

  public static ResourceIndexer create(File indexDirectory) {
    return new ResourceIndexer(indexDirectory);
  }

  private ResourceIndexer(File indexDirectory) {
    try {
      this.index =
          indexDirectory == null
              ? new ByteBuffersDirectory()
              : new MMapDirectory(indexDirectory.toPath());
      this.analyzer = new StandardAnalyzer();
      this.writer = new IndexWriter(index, new IndexWriterConfig(this.analyzer));
      this.searcherManager = new SearcherManager(writer, true, true, null);
      this.nrtReopenThread =
          new ControlledRealTimeReopenThread<>(writer, searcherManager, 1.0, 0.1);
      nrtReopenThread.setName("Resource index NRT reopen thread");
      nrtReopenThread.setDaemon(true);
      nrtReopenThread.start();
    } catch (IOException e) {
      throw new KlabIOException(e);
    }
  }

  /** True when the on-disk index uses the current field and ranking schema. */
  public boolean hasCurrentSchema() {
    for (Map.Entry<String, String> entry : writer.getLiveCommitData()) {
      if (SCHEMA_VERSION_KEY.equals(entry.getKey())) {
        return SCHEMA_VERSION.equals(entry.getValue());
      }
    }
    return false;
  }

  /** Replace any previous entry for this URN. ResourceInfo supplies owner/catalog metadata. */
  public void index(Resource resource, ResourceInfo info) {
    Objects.requireNonNull(resource);
    try {
      writer.updateDocument(new Term("id", resource.getUrn()), createDocument(resource, info));
    } catch (IOException e) {
      throw new KlabIOException(e);
    }
  }

  /** Compatibility overload for callers that do not maintain catalog metadata. */
  public void index(Resource resource) {
    index(resource, null);
  }

  public void delete(String urn) {
    try {
      writer.deleteDocuments(new Term("id", urn));
    } catch (IOException e) {
      throw new KlabIOException(e);
    }
  }

  public void clear() {
    try {
      writer.deleteAll();
    } catch (IOException e) {
      throw new KlabIOException(e);
    }
  }

  private Document createDocument(Resource resource, ResourceInfo info) {
    Document document = new Document();
    String urn = resource.getUrn();
    String normalizedUrn = normalize(urn);
    Urn urnObject = Urn.of(urn);

    document.add(new StringField("id", urn, Store.YES));
    document.add(new StringField("urn_exact", normalizedUrn, Store.NO));
    document.add(new TextField("urn", urn, Store.NO));

    addName(document, resource.getLocalName());
    var metadata = resource.getMetadata();
    if (metadata != null) {
      addName(document, urnObject.getResourceId());
      addName(document, metadata.get(Metadata.DC_NAME));
      addName(document, metadata.get(Metadata.DC_LABEL));
      addName(document, metadata.get(Metadata.DC_TITLE));
      addName(document, metadata.get(Metadata.RDFS_LABEL));
      addText(document, "description", metadata.get(Metadata.DC_DESCRIPTION));
      addText(document, "description", metadata.get(Metadata.DC_DESCRIPTION_ABSTRACT));
      addText(document, "description", metadata.get(Metadata.DC_COMMENT));
      addText(document, "description", metadata.get(Metadata.RDFS_COMMENT));
      for (Map.Entry<String, Object> entry : metadata.entrySet()) {
        addFlattened(document, "metadata", entry.getValue());
      }
    }

    // The final URN segment is the best fallback display/search name.
    int separator = Math.max(urn.lastIndexOf(':'), urn.lastIndexOf('/'));
    addName(document, separator < 0 ? urn : urn.substring(separator + 1));
    addText(document, "adapter", resource.getAdapterType());
    if (info != null) {
      addText(document, "owner", info.getOwner());
      if (info.getMetadata() != null) {
        addName(document, info.getMetadata().get(Metadata.DC_NAME));
        addName(document, info.getMetadata().get(Metadata.DC_LABEL));
        addName(document, info.getMetadata().get(Metadata.DC_TITLE));
        addName(document, info.getMetadata().get(Metadata.RDFS_LABEL));
        addText(document, "description", info.getMetadata().get(Metadata.DC_DESCRIPTION));
        addText(document, "description", info.getMetadata().get(Metadata.DC_DESCRIPTION_ABSTRACT));
        addText(document, "description", info.getMetadata().get(Metadata.DC_COMMENT));
        addText(document, "description", info.getMetadata().get(Metadata.RDFS_COMMENT));
        for (Object value : info.getMetadata().values()) {
          addFlattened(document, "metadata", value);
        }
      }
    }
    return document;
  }

  private static void addName(Document document, Object value) {
    if (value != null && !value.toString().isBlank()) {
      String text = value.toString();
      document.add(new TextField("name", text, Store.NO));
      document.add(new StringField("name_exact", normalize(text), Store.NO));
    }
  }

  private static void addText(Document document, String field, Object value) {
    if (value != null && !value.toString().isBlank()) {
      document.add(new TextField(field, value.toString(), Store.NO));
    }
  }

  private static void addFlattened(Document document, String field, Object value) {
    if (value instanceof Map<?, ?> map) {
      map.forEach((key, nested) -> addFlattened(document, field, nested));
    } else if (value instanceof Collection<?> collection) {
      collection.forEach(nested -> addFlattened(document, field, nested));
    } else if (value != null) {
      addText(document, field, value);
    }
  }

  public void commitChanges() {
    try {
      writer.setLiveCommitData(Map.of(SCHEMA_VERSION_KEY, SCHEMA_VERSION).entrySet());
      writer.commit();
      searcherManager.maybeRefreshBlocking();
    } catch (IOException e) {
      throw new KlabIOException(e);
    }
  }

  public boolean ensureClosed() {
    nrtReopenThread.close();
    try {
      writer.setLiveCommitData(Map.of(SCHEMA_VERSION_KEY, SCHEMA_VERSION).entrySet());
      writer.commit();
      searcherManager.close();
      writer.close();
      index.close();
      return true;
    } catch (IOException e) {
      Logging.INSTANCE.error(e);
      return false;
    }
  }

  public List<SemanticMatch> query(String query) {
    return query(query, MAX_RESULT_COUNT);
  }

  private Query buildQuery(String input) {
    String normalized = normalize(input);
    if (normalized.isBlank()) {
      return new MatchAllDocsQuery();
    }

    BooleanQuery.Builder result = new BooleanQuery.Builder();
    result.add(
        boost(new TermQuery(new Term("urn_exact", normalized)), 30), BooleanClause.Occur.SHOULD);
    result.add(
        boost(new TermQuery(new Term("name_exact", normalized)), 25), BooleanClause.Occur.SHOULD);
    result.add(
        boost(new PrefixQuery(new Term("urn_exact", normalized)), 18), BooleanClause.Occur.SHOULD);
    result.add(
        boost(new PrefixQuery(new Term("name_exact", normalized)), 16), BooleanClause.Occur.SHOULD);

    for (String term : normalized.split("[^\\p{L}\\p{N}]+")) {
      if (term.isBlank()) {
        continue;
      }
      BooleanQuery.Builder token = new BooleanQuery.Builder();
      addTermAlternatives(token, "urn", term, 12);
      addTermAlternatives(token, "name", term, 10);
      addTermAlternatives(token, "description", term, 4);
      addTermAlternatives(token, "metadata", term, 3);
      addTermAlternatives(token, "adapter", term, 5);
      addTermAlternatives(token, "owner", term, 5);
      result.add(token.build(), BooleanClause.Occur.MUST);
    }
    return result.build();
  }

  private static void addTermAlternatives(
      BooleanQuery.Builder query, String field, String term, float boost) {
    query.add(boost(new TermQuery(new Term(field, term)), boost), BooleanClause.Occur.SHOULD);
    query.add(
        boost(new PrefixQuery(new Term(field, term)), boost * 0.8f), BooleanClause.Occur.SHOULD);
    if (term.length() >= 4) {
      query.add(
          boost(
              new FuzzyQuery(new Term(field, term), 1, Math.min(2, term.length() - 1)),
              boost * 0.35f),
          BooleanClause.Occur.SHOULD);
    }
  }

  private static Query boost(Query query, float boost) {
    return new BoostQuery(query, boost);
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").strip().toLowerCase(Locale.ROOT);
  }

  public List<SemanticMatch> query(String query, int maxResults) {
    List<SemanticMatch> ret = new ArrayList<>();
    IndexSearcher searcher;
    try {
      searcher = searcherManager.acquire();
    } catch (IOException e) {
      throw new KlabIOException(e);
    }

    try {
      TopDocs docs = searcher.search(buildQuery(query), Math.max(1, maxResults));
      for (ScoreDoc hit : docs.scoreDocs) {
        Document document = searcher.storedFields().document(hit.doc);
        SemanticMatch match = new SemanticMatch();
        match.setId(document.get("id"));
        match.setName(document.get("id"));
        match.setScore(hit.score);
        match.setMatchType(SemanticMatch.Type.RESOURCE);
        ret.add(match);
      }
      return ret;
    } catch (IOException e) {
      throw new KlabIOException(e);
    } finally {
      try {
        searcherManager.release(searcher);
      } catch (IOException e) {
        throw new KlabIOException(e);
      }
    }
  }
}
