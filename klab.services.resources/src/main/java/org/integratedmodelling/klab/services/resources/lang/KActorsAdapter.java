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
import org.integratedmodelling.kactors.api.IKActorsStatement.Do;
import org.integratedmodelling.kactors.api.IKActorsStatement.Fail;
import org.integratedmodelling.kactors.api.IKActorsStatement.FireValue;
import org.integratedmodelling.kactors.api.IKActorsStatement.For;
import org.integratedmodelling.kactors.api.IKActorsStatement.If;
import org.integratedmodelling.kactors.api.IKActorsStatement.Instantiation;
import org.integratedmodelling.kactors.api.IKActorsStatement.TextBlock;
import org.integratedmodelling.kactors.api.IKActorsStatement.While;
import org.integratedmodelling.kactors.api.IKActorsValue;
import org.integratedmodelling.kactors.kactors.Model;
import org.integratedmodelling.kactors.model.KActors;
import org.integratedmodelling.kactors.model.KActorsArguments;
import org.integratedmodelling.kim.api.IKimAnnotation;
import org.integratedmodelling.kim.api.IParameters;
import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.impl.ParametersImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsActionImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsArgumentsImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsStatementImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsStatementImpl.AssignmentImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsStatementImpl.CallImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsStatementImpl.TextBlockImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsValueImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsValueImpl.ConstructorImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Arguments;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.ConcurrentGroup;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue.Constructor;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue.DataType;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue.ExpressionType;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.Notification.Level;
import org.integratedmodelling.klab.exceptions.KlabException;
import org.integratedmodelling.klab.exceptions.KlabIOException;
import org.integratedmodelling.klab.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.exceptions.KlabValidationException;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.klab.utilities.Utils.Lang;
import org.integratedmodelling.klab.utils.Pair;
import org.integratedmodelling.klab.utils.Triple;

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

	public KActorsBehavior readBehavior(URL behaviorUrl) {
		IKActorsBehavior behavior = declare(behaviorUrl);
		return adapt(behavior);
	}

	public KActorsBehavior readBehavior(File behaviorFile) {
		IKActorsBehavior behavior = declare(behaviorFile);
		return adapt(behavior);
	}

	private KActorsBehavior adapt(IKActorsBehavior behavior) {

		KActorsBehaviorImpl ret = new KActorsBehaviorImpl();

		ret.setUrn(behavior.getName());
		ret.setDeprecated(behavior.isDeprecated());
		if (behavior.isErrors()) {
		    ret.getNotifications().add(Notification.of("Errors in behavior " + behavior.getName(), Level.Error));
		}
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
			((KActorsStatementImpl.DoImpl) ret).setBody(adaptStatement(((Do) code).getBody()));
			((KActorsStatementImpl.DoImpl) ret).setCondition(adaptValue(((Do) code).getCondition()));
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
			((KActorsStatementImpl.IfImpl) ret).setCondition(adaptValue(((If) code).getCondition()));
			((KActorsStatementImpl.IfImpl) ret).setThenBody(adaptStatement(((If) code).getThen()));
			((KActorsStatementImpl.IfImpl) ret).setElseBody(adaptStatement(((If) code).getElse()));
			for (Pair<IKActorsValue, IKActorsStatement> elseif : ((If) code).getElseIfs()) {
				((KActorsStatementImpl.IfImpl) ret).getElseIfs().add(org.integratedmodelling.klab.api.collections.Pair
						.of(adaptValue(elseif.getFirst()), adaptStatement(elseif.getSecond())));
			}
			break;
		case INSTANTIATION:
			ret = new KActorsStatementImpl.InstantiationImpl();
			((KActorsStatementImpl.InstantiationImpl) ret).setBehavior(((Instantiation) code).getBehavior());
			((KActorsStatementImpl.InstantiationImpl) ret).setActorBaseName(((Instantiation) code).getActorBaseName());
			((KActorsStatementImpl.InstantiationImpl) ret)
					.setArguments(adaptParameters(((Instantiation) code).getArguments()));
			for (Triple<IKActorsValue, IKActorsStatement, String> action : ((Instantiation) code).getActions()) {
				((KActorsStatementImpl.InstantiationImpl) ret).getActions()
						.add(org.integratedmodelling.klab.api.collections.Triple.of(adaptValue(action.getFirst()),
								adaptStatement(action.getSecond()), action.getThird()));
			}
			break;
		case SEQUENCE:
			ret = new KActorsStatementImpl.SequenceImpl();
			for (IKActorsStatement statement : ((IKActorsStatement.Sequence) code).getStatements()) {
				((KActorsStatementImpl.SequenceImpl) ret).getStatements().add(adaptStatement(statement));
			}
			break;
		case TEXT_BLOCK:
			ret = new KActorsStatementImpl.TextBlockImpl();
			((TextBlockImpl) ret).setText(((TextBlock) code).getText());
			break;
		case WHILE_STATEMENT:
			ret = new KActorsStatementImpl.WhileImpl();
			((KActorsStatementImpl.WhileImpl) ret).setBody(adaptStatement(((While) code).getBody()));
			((KActorsStatementImpl.WhileImpl) ret).setCondition(adaptValue(((While) code).getCondition()));
			break;
		default:
			// you never know
			throw new KlabInternalErrorException("k.Actors adapter: can't handle statement type " + code.getType());
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
		ret.getNamedKeys().addAll(parms.getNamedKeys());
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

		if (value == null) {
			return null;
		}

		KActorsValueImpl ret = new KActorsValueImpl();

		ret.setType(ValueType.valueOf(value.getType().name()));
		ret.setDeferred(value.isDeferred());
		ret.setExclusive(value.isExclusive());
		ret.setDeprecated(value.isDeprecated());
		ret.setDeprecation(value.getDeprecation());
		ret.setSourceCode(value.getSourceCode());
		ret.setFalseCase(adaptValue(value.getFalseCase()));
		ret.setTrueCase(adaptValue(value.getTrueCase()));
		ret.setFirstLine(value.getFirstLine());
		ret.setLastLine(value.getLastLine());
		ret.setFirstCharOffset(value.getFirstCharOffset());
		ret.setLastCharOffset(value.getLastCharOffset());
		ret.setMetadata(Metadata.create(value.getMetadata()));
		ret.setExpressionType(ExpressionType.valueOf(value.getExpressionType().name()));
		ret.setTag(value.getTag());
		
		if (value.getStatedValue() != null) {
			ret.setStatedValue(Literal.of(value.getStatedValue()));
		}
		if (value.getCallChain() != null) {
			for (Call call : value.getCallChain()) {
				ret.getCallChain().add((KActorsStatement.Call) adaptStatement(call));
			}
		}
		if (value instanceof org.integratedmodelling.kactors.model.KActorsValue
				&& ((org.integratedmodelling.kactors.model.KActorsValue) value).getConstructor() != null) {
			ret.setConstructor(
					adaptConstructor(((org.integratedmodelling.kactors.model.KActorsValue) value).getConstructor()));
		}
		if (value.getCast() != null) {
			ret.setCast(DataType.valueOf(value.getCast().name()));
		}

		return ret;
	}

	private Constructor adaptConstructor(org.integratedmodelling.kactors.model.KActorsValue.Constructor constructor) {
		ConstructorImpl ret = new ConstructorImpl();
		ret.setClassname(constructor.getClassname());
		ret.setClasspath(constructor.getClasspath());
		ret.setComponent(constructor.getComponent());
		ret.setArguments(adaptArguments(constructor.getArguments()));
		return ret;
	}

	Arguments adaptArguments(KActorsArguments arguments) {

		if (arguments == null) {
			return null;
		}

		org.integratedmodelling.klab.utils.Parameters<String> parms = (org.integratedmodelling.klab.utils.Parameters<String>) arguments;
		KActorsArgumentsImpl ret = new KActorsArgumentsImpl();
		ret.putAll(parms.getData());
		ret.getUnnamedKeys().addAll(parms.getUnnamedKeys());
		ret.getNamedKeys().addAll(parms.getNamedKeys());
		ret.setTemplateVariables(adaptParameters(parms.getTemplateVariables()));
		ret.getMetadataKeys().addAll(arguments.getMetadataKeys());
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
