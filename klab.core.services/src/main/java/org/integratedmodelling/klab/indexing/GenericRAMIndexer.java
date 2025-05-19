package org.integratedmodelling.klab.indexing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.integratedmodelling.common.logging.Logging;

/**
 * Generic indexer that holds both the index AND the objects themselves in
 * memory. Don't use for anything very large. Queries return the matching
 * objects directly.
 * 
 * @author Ferd
 *
 * @param <T>
 */
public abstract class GenericRAMIndexer<T> {

	private Directory index;
	private IndexWriter writer;
	private StandardAnalyzer analyzer;
	private ReferenceManager<IndexSearcher> searcherManager;
	private ControlledRealTimeReopenThread<IndexSearcher> nrtReopenThread;
	private Map<String, T> data = Collections.synchronizedMap(new HashMap<>());

	public static final int MAX_RESULT_COUNT = 9;

	public GenericRAMIndexer(Class<? extends T> cls) {
		try {
		    /*
		     * FIXME consider using MMapDirectory instead
		     */
			this.index = new ByteBuffersDirectory();
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

	public void index(T resource) {

		try {
			String id = getResourceId(resource);
			Document document = new Document();
			document.add(new StringField("id", id, Store.YES));
			document.add(new TextField("name", getResourceLabel(resource), Store.YES));
			document.add(new TextField("description", getResourceDescription(resource), Store.YES));

			Metadata metadata = getResourceMetadata(resource);
			if (metadata != null) {
				for (String key : metadata.keySet()) {
					document.add(new TextField(key, metadata.get(key).toString(), Store.YES));
				}
			}

			this.writer.addDocument(document);
			this.data.put(id, resource);

		} catch (IOException e) {
			throw new KlabIOException(e);
		}
	}

	/**
	 * Metadata associated with resource, or null.
	 * 
	 * @param resource
	 * @return
	 */
	protected abstract Metadata getResourceMetadata(T resource);

	/**
	 * Don't return null, ever.
	 * 
	 * @param resource
	 * @return
	 */
	protected abstract String getResourceDescription(T resource);

	/**
	 * Don't return null, ever.
	 * 
	 * @param resource
	 * @return
	 */
	protected abstract String getResourceLabel(T resource);

	/**
	 * Don't return null, ever.
	 * 
	 * @param resource
	 * @return
	 */
	protected abstract String getResourceId(T resource);

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

	public List<T> query(String query) {
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

	public List<T> query(String query, int maxResults) {

		List<T> ret = new ArrayList<>();

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
					ret.add(data.get(document.get("id")));
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

	public T getResource(String id) {
		return data.get(id);
	}
}
