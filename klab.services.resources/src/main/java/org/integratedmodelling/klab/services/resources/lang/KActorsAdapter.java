package org.integratedmodelling.klab.services.resources.lang;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.eclipse.xtext.testing.IInjectorProvider;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.integratedmodelling.kactors.api.IKActorsAction;
import org.integratedmodelling.kactors.api.IKActorsBehavior;
import org.integratedmodelling.kactors.api.IKActorsStatement;
import org.integratedmodelling.kactors.api.IKActorsStatement.Assert.Assertion;
import org.integratedmodelling.kactors.api.IKActorsStatement.Call;
import org.integratedmodelling.kactors.api.IKActorsStatement.Fail;
import org.integratedmodelling.kactors.api.IKActorsStatement.FireValue;
import org.integratedmodelling.kactors.api.IKActorsStatement.For;
import org.integratedmodelling.kactors.api.IKActorsValue;
import org.integratedmodelling.kactors.kactors.Model;
import org.integratedmodelling.kactors.model.KActors;
import org.integratedmodelling.kim.api.IKimAnnotation;
import org.integratedmodelling.kim.api.IParameters;
import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.impl.ParametersImpl;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsActionImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsStatementImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsStatementImpl.AssignmentImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsStatementImpl.CallImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsValueImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.ConcurrentGroup;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue;
import org.integratedmodelling.klab.exceptions.KlabException;
import org.integratedmodelling.klab.exceptions.KlabIOException;
import org.integratedmodelling.klab.exceptions.KlabValidationException;
import org.integratedmodelling.klab.utils.Pair;
import org.integratedmodelling.klab.utils.Triple;
import org.integratedmodelling.klab.utils.Utils;
import org.integratedmodelling.klab.utils.Utils.Lang;

import com.google.inject.Inject;
import com.google.inject.Injector;

public enum KActorsAdapter {

	INSTANCE;

	@Inject
	private ParseHelper<Model> kActorsParser;

	private KActorsAdapter() {
		IInjectorProvider injectorProvider = new KactorsInjectorProvider();
		Injector injector = injectorProvider.getInjector();
		if (injector != null) {
			injector.injectMembers(this);
		}
	}

	public KActorsBehavior readBehavior(File behaviorFile) {
		IKActorsBehavior behavior = declare(behaviorFile);
		return adapt(behavior);
	}

	private KActorsBehavior adapt(IKActorsBehavior behavior) {

		KActorsBehaviorImpl ret = new KActorsBehaviorImpl();

		ret.setName(behavior.getName());
		ret.setDeprecated(behavior.isDeprecated());
		ret.setErrors(behavior.isErrors());
		ret.setDescription(behavior.getDescription());
		ret.setLogo(behavior.getLogo());
		ret.setLabel(behavior.getName());
		ret.setSourceCode(behavior.getSourceCode());
		ret.setDeprecation(behavior.getDeprecation());
		ret.getImports().addAll(behavior.getImports());
		ret.getLocales().addAll(behavior.getLocales());
		ret.setMetadata(Utils.Lang.makeMetadata(behavior.getMetadata()));
		ret.setTag(behavior.getTag());
		ret.setType(KActorsBehavior.Type.valueOf(behavior.getType().name()));

		for (IKActorsAction action : behavior.getActions()) {
			ret.getActions().add(adaptAction(action));
		}

		for (IKimAnnotation annotation : behavior.getAnnotations()) {
			ret.getAnnotations().add(Utils.Lang.makeAnnotation(annotation));
		}

		return ret;
	}

	private KActorsAction adaptAction(IKActorsAction action) {

		KActorsActionImpl ret = new KActorsActionImpl();

		Lang.copyStatementData(action, ret);

		ret.setName(action.getName());
		ret.getArgumentNames().addAll(action.getArgumentNames());
		ret.setCode(adaptStatement(action.getCode()));

		return ret;
	}

	private KActorsStatement adaptStatement(IKActorsStatement code) {
		
		KActorsStatementImpl ret = null;

		if (code == null) {
			return ret;
		}
		
		switch (code.getType()) {
		case ACTION_CALL:
			ret = new KActorsStatementImpl.CallImpl();
			((CallImpl) ret).setMessage(((IKActorsStatement.Call) code).getMessage());
			for (Triple<IKActorsValue, IKActorsStatement, String> action : ((IKActorsStatement.Call) code)
					.getActions()) {
				((CallImpl) ret).getActions().add(org.integratedmodelling.klab.api.collections.Triple
						.of(adaptValue(action.getFirst()), adaptStatement(action.getSecond()), action.getThird()));
			}
			((CallImpl) ret).setCallId(((IKActorsStatement.Call) code).getCallId());
			((CallImpl) ret).setRecipient(((IKActorsStatement.Call) code).getRecipient());
			((CallImpl) ret).setGroup((ConcurrentGroup) adaptStatement(((IKActorsStatement.Call) code).getGroup()));
			((CallImpl) ret).setArguments(adaptParameters(((IKActorsStatement.Call) code).getArguments()));
			for (Call call : ((IKActorsStatement.Call) code).getChainedCalls()) {
				((CallImpl) ret).getChainedCalls().add((KActorsStatement.Call) adaptStatement(call));
			}
			break;
		case ASSERT_STATEMENT:
			ret = new KActorsStatementImpl.AssertImpl();
			for (Assertion assertion : ((IKActorsStatement.Assert) code).getAssertions()) {
				((KActorsStatementImpl.AssertImpl) ret).getAssertions().add(adaptAssertion(assertion));
			}
			break;
		case ASSIGNMENT:
			ret = new KActorsStatementImpl.AssignmentImpl();
			((AssignmentImpl) ret).setRecipient(((IKActorsStatement.Assignment) code).getRecipient());
			((AssignmentImpl) ret).setVariable(((IKActorsStatement.Assignment) code).getVariable());
			((AssignmentImpl) ret).setValue(adaptValue(((IKActorsStatement.Assignment) code).getValue()));
			((AssignmentImpl) ret).setScope(
					KActorsStatement.Assignment.Scope.valueOf(((IKActorsStatement.Assignment) code).getScope().name()));
			break;
		case BREAK_STATEMENT:
			ret = new KActorsStatementImpl.BreakImpl();
			break;
		case CONCURRENT_GROUP:
			ret = new KActorsStatementImpl.ConcurrentGroupImpl();
			for (IKActorsStatement statement : ((IKActorsStatement.ConcurrentGroup) code).getStatements()) {
				((KActorsStatementImpl.ConcurrentGroupImpl) ret).getStatements().add(adaptStatement(statement));
			}
			for (String key : ((IKActorsStatement.ConcurrentGroup) code).getGroupMetadata().keySet()) {
				((KActorsStatementImpl.ConcurrentGroupImpl) ret).getGroupMetadata().put(key,
						adaptValue(((IKActorsStatement.ConcurrentGroup) code).getGroupMetadata().get(key)));
			}
			for (Pair<IKActorsValue, IKActorsStatement> action : ((IKActorsStatement.ConcurrentGroup) code)
					.getGroupActions()) {
				((KActorsStatementImpl.ConcurrentGroupImpl) ret).getGroupActions()
						.add(org.integratedmodelling.klab.api.collections.Pair.of(adaptValue(action.getFirst()),
								adaptStatement(action.getSecond())));
			}
			break;
		case DO_STATEMENT:
			ret = new KActorsStatementImpl.DoImpl();
			break;
		case FAIL_STATEMENT:
			ret = new KActorsStatementImpl.FailImpl();
			((KActorsStatementImpl.FailImpl) ret).setMessage(((Fail) code).getMessage());
			break;
		case FIRE_VALUE:
			ret = new KActorsStatementImpl.FireImpl();
			((KActorsStatementImpl.FireImpl) ret).setValue(adaptValue(((FireValue) code).getValue()));
			break;
		case FOR_STATEMENT:
			ret = new KActorsStatementImpl.ForImpl();
			((KActorsStatementImpl.ForImpl) ret).setVariable(((For) code).getVariable());
			((KActorsStatementImpl.ForImpl) ret).setIterable(adaptValue(((For) code).getIterable()));
			((KActorsStatementImpl.ForImpl) ret).setBody(adaptStatement(((For) code).getBody()));
			break;
		case IF_STATEMENT:
			ret = new KActorsStatementImpl.IfImpl();
			break;
		case INSTANTIATION:
			ret = new KActorsStatementImpl.InstantiationImpl();
			break;
		case SEQUENCE:
			ret = new KActorsStatementImpl.SequenceImpl();
			for (IKActorsStatement statement : ((IKActorsStatement.Sequence) code).getStatements()) {
				((KActorsStatementImpl.SequenceImpl) ret).getStatements().add(adaptStatement(statement));
			}
			break;
		case TEXT_BLOCK:
			ret = new KActorsStatementImpl.TextBlockImpl();
			break;
		case WHILE_STATEMENT:
			ret = new KActorsStatementImpl.WhileImpl();
			break;
		default:
			break;
		}

		Lang.copyStatementData(code, ret);

		return ret;
	}

	public static Parameters<String> adaptParameters(IParameters<String> arguments) {

		if (arguments == null) {
			return null;
		}
		
		org.integratedmodelling.klab.utils.Parameters<String> parms = (org.integratedmodelling.klab.utils.Parameters<String>) arguments;
		ParametersImpl<String> ret = new ParametersImpl<String>();
		ret.putAll(parms.getData());
		ret.getUnnamedKeys().addAll(parms.getUnnamedKeys());
		ret.setTemplateVariables(adaptParameters(parms.getTemplateVariables()));
		return ret;
	}

	private KActorsStatement.Assert.Assertion adaptAssertion(Assertion assertion) {
		KActorsStatementImpl.AssertImpl.AssertionImpl ret = new KActorsStatementImpl.AssertImpl.AssertionImpl();
		for (Call call : assertion.getCalls()) {
			ret.getCalls().add((KActorsStatement.Call) adaptStatement(call));
		}
		if (assertion.getValue() != null) {
			ret.setValue(adaptValue(assertion.getValue()));
		}
		if (assertion.getExpression() != null) {
			ret.setExpression(adaptValue(assertion.getExpression()));
		}
		return ret;
	}

	private KActorsValue adaptValue(IKActorsValue value) {

		KActorsValueImpl ret = new KActorsValueImpl();

		ret.setType(ValueType.valueOf(value.getType().name()));

		switch (value.getType()) {
		case ANNOTATION:
		case ANYTHING:
		case ANYTRUE:
		case ANYVALUE:
			break;
		case BOOLEAN:
		case NUMBER:
		case STRING:
			ret.setStatedValue(Literal.of(value.getStatedValue()));
			break;
		case CALLCHAIN:
			break;
		case CLASS:
			break;
		case COMPONENT:
			break;
		case CONSTANT:
			break;
		case DATE:
			break;
		case EMPTY:
			break;
		case ERROR:
			break;
		case EXPRESSION:
			break;
		case IDENTIFIER:
			break;
		case LIST:
			break;
		case LOCALIZED_KEY:
			break;
		case MAP:
			break;
		case NODATA:
			break;
		case NUMBERED_PATTERN:
			break;
		case OBJECT:
			break;
		case OBSERVABLE:
			break;
		case OBSERVATION:
			break;
		case QUANTITY:
			break;
		case RANGE:
			break;
		case REGEXP:
			break;
		case SET:
			break;
		case TABLE:
			break;
		case TREE:
			break;
		case TYPE:
			break;
		case URN:
			break;
		default:
			break;

		}
		return ret;
	}

	public IKActorsBehavior declare(URL url) throws KlabException {
		try (InputStream stream = url.openStream()) {
			return declare(stream);
		} catch (Exception e) {
			throw new KlabIOException(e);
		}
	}

	public IKActorsBehavior declare(File file) throws KlabException {
		try (InputStream stream = new FileInputStream(file)) {
			return declare(stream);
		} catch (Exception e) {
			throw new KlabIOException(e);
		}
	}

	public IKActorsBehavior declare(InputStream file) throws KlabValidationException {
		IKActorsBehavior ret = null;
		try {
			String definition = IOUtils.toString(file, StandardCharsets.UTF_8);
			Model model = kActorsParser.parse(definition);
			ret = KActors.INSTANCE.declare(model);
		} catch (Exception e) {
			throw new KlabValidationException(e);
		}
		return ret;
	}
}
