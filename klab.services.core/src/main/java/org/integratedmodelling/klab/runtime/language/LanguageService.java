package org.integratedmodelling.klab.runtime.language;

import java.util.List;

import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.Expression.CompilerOption;
import org.integratedmodelling.klab.api.knowledge.Expression.Descriptor;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public class LanguageService implements Language {

	
	
	@Override
	public Descriptor describe(String expression, String language, Scope scope, CompilerOption... options) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Expression compile(String expression, String language, CompilerOption... options) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Notification> validate(ServiceCall call) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Notification> validate(Annotation annotation) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> T execute(ServiceCall call, Scope scope, Class<T> resultClass) {
		// TODO Auto-generated method stub
		return null;
	}

}
