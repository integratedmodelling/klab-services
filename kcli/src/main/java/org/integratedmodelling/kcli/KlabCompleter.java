package org.integratedmodelling.kcli;

import java.util.List;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

/**
 * TODO add support for semantic search, completion of scripts and test cases.
 * Semantic search must use the reasoner from the current user's scope.
 * 
 * @author Ferd
 *
 */
public class KlabCompleter implements Completer {

	private Completer delegate;

	public void resetSemanticSearch() {
		// TODO
	}

	/**
	 * Recognize the type of knowledge we are trying to match. If the current word
	 * is empty, suggest "all" resources if possible or return null. If we are
	 * matching concepts, use the semantic search context, which is reset at every
	 * command execution.
	 * 
	 * @param line
	 * @return
	 */
	private Pair<KnowledgeClass, String> classifyMatch(ParsedLine line) {
		return null;
	}

	public KlabCompleter(Completer completer) {
		this.delegate = completer;
	}

	@Override
	public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
		Pair<KnowledgeClass, String> match = classifyMatch(line);
		if (match != null) {
			// take over
			return;
		}
		delegate.complete(reader, line, candidates);
	}

}
