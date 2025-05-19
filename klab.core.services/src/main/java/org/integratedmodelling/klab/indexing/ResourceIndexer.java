package org.integratedmodelling.klab.indexing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.ControlledRealTimeReopenThread;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticMatch;
import org.integratedmodelling.common.logging.Logging;

public class ResourceIndexer {

	private Directory index;
	private IndexWriter writer;
	private StandardAnalyzer analyzer;
	private ReferenceManager<IndexSearcher> searcherManager;
	private ControlledRealTimeReopenThread<IndexSearcher> nrtReopenThread;
	
	public static final int MAX_RESULT_COUNT = 9;

	private ResourceIndexer() {
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

	public void index(Resource resource) {

		try {

			Document document = new Document();

			Urn urn = Urn.of(resource.getUrn());

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

	public void commitChanges() {
		try {
			this.writer.commit();
		} catch (IOException e) {
			throw new KlabIOException(e);
		}
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

	public List<SemanticMatch> query(String query) {
		return query(query, MAX_RESULT_COUNT);
	}

	private Query buildQuery(String currentTerm) {
		QueryParser parser = new QueryParser("name", analyzer);
		// parser.setAllowLeadingWildcard(true);
		try {
			// hai voglia
			return parser.parse(currentTerm + "*");
		} catch (ParseException e) {
			throw new KlabValidationException(e);
		}
	}

	public List<SemanticMatch> query(String query, int maxResults) {

		List<SemanticMatch> ret = new ArrayList<>();

		Set<String> ids = new HashSet<>();

		IndexSearcher searcher;
		try {
			searcher = searcherManager.acquire();
		} catch (IOException e) {
			// adorable exception management
			throw new KlabIOException(e);
		}

		try {
			TopDocs docs = searcher.search(buildQuery(query), maxResults);
			ScoreDoc[] hits = docs.scoreDocs;

			for (ScoreDoc hit : hits) {

				Document document = searcher.storedFields().document(hit.doc);

				if (!ids.contains(document.get("id"))) {

					SemanticMatch match = new SemanticMatch();
					match.setId(document.get("id"));
					match.setName(document.get("name"));
					match.setDescription(document.get("description"));
					match.setScore(hit.score);
					match.setMatchType(SemanticMatch.Type.RESOURCE);

					ret.add(match);
					ids.add(document.get("id"));
				}
			}

		} catch (Exception e) {
			throw new KlabIOException(e);
		} finally {
			try {
				searcherManager.release(searcher);
			} catch (IOException e) {
				// unbelievable, they want it in finally and make it throw a checked
				// exception
				throw new KlabIOException(e);
			}
		}

		return ret;
	}
}
