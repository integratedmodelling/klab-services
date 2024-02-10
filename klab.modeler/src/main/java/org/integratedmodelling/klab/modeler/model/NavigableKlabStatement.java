package org.integratedmodelling.klab.modeler.model;

import java.util.Collection;
import java.util.List;

import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public class NavigableKlabStatement extends NavigableKlabAsset<KlabStatement> implements KlabStatement {

	private static final long serialVersionUID = -6767482412135943192L;

	public NavigableKlabStatement(KlabStatement asset, NavigableKlabAsset<?> parent) {
		super(asset, parent);
	}

	@Override
	public List<Annotation> getAnnotations() {
		return delegate.getAnnotations();
	}

	@Override
	public String getDeprecation() {
		return delegate.getDeprecation();
	}

	@Override
	public int getLength() {
		return delegate.getLength();
	}

	@Override
	public Collection<Notification> getNotifications() {
		return delegate.getNotifications();
	}

	@Override
	public int getOffsetInDocument() {
		return delegate.getOffsetInDocument();
	}

	@Override
	public boolean isDeprecated() {
		return delegate.isDeprecated();
	}

	@Override
	public String sourceCode() {
		return delegate.sourceCode();
	}

	@Override
	public String getNamespace() {
		// TODO Auto-generated method stub
		return delegate.getNamespace();
	}

	public NavigableKlabDocument<?, ?> document() {
		var parent = this.parent();
		while (parent instanceof NavigableKlabAsset asset && !(parent instanceof NavigableKlabDocument)) {
			parent = asset.parent();
		}
		return (NavigableKlabDocument<?,?>)parent;
	}
	
	@Override
	public Scope getScope() {
		return delegate.getScope();
	}

	@Override
	public Object[] children() {
		if (delegate instanceof KimConceptStatement concept) {
			return concept.getChildren().stream().map(c -> new NavigableKlabStatement(c, this)).toArray();
		}
		return super.children();
	}

	@Override
	public void visit(KlabStatementVisitor visitor) {
		delegate.visit(visitor);
	}
	
}
