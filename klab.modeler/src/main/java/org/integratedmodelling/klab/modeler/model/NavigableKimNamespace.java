package org.integratedmodelling.klab.modeler.model;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;

public class NavigableKimNamespace extends NavigableKlabDocument<KlabStatement, KimNamespace> implements KimNamespace {

	@Serial
	private static final long serialVersionUID = 3213955882357790089L;

	public NavigableKimNamespace(KimNamespace document, NavigableKlabAsset<?> parent) {
		super(document, parent);
	}

	@Override
	protected List<? extends NavigableKlabStatement> createChildren() {
		return getStatements().stream().map(s -> new NavigableKlabStatement(s, this)).toList();
	}

//	@Override
//	public Parameters<String> getDefines() {
//		return delegate.getDefines();
//	}

	@Override
	public Collection<String> getDisjointNamespaces() {
		return delegate.getDisjointNamespaces();
	}

	@Override
	public Geometry getCoverage() {
		return delegate.getCoverage();
	}

	@Override
	public Map<String, List<String>> getImports() {
		return delegate.getImports();
	}

	@Override
	public String getScriptId() {
		return delegate.getScriptId();
	}

	@Override
	public String getTestCaseId() {
		return delegate.getTestCaseId();
	}

	@Override
	public boolean isScenario() {
		return delegate.isScenario();
	}

	@Override
	public boolean isWorldviewBound() {
		return delegate.isWorldviewBound();
	}

	@Override
	public Set<String> importedNamespaces(boolean withinType) {
		return delegate.importedNamespaces(withinType);
	}

	@Override
	public void visit(DocumentVisitor<KlabStatement> visitor) {
		delegate.visit(visitor);
	}
	
}
