package org.integratedmodelling.klab.services.resources.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.integratedmodelling.kim.api.IContextualizable;
import org.integratedmodelling.kim.api.IKimAcknowledgement;
import org.integratedmodelling.kim.api.IKimClassification;
import org.integratedmodelling.kim.api.IKimConcept;
import org.integratedmodelling.kim.api.IKimConceptStatement;
import org.integratedmodelling.kim.api.IKimConceptStatement.ApplicableConcept;
import org.integratedmodelling.kim.api.IKimConceptStatement.DescriptionType;
import org.integratedmodelling.kim.api.IKimConceptStatement.ParentConcept;
import org.integratedmodelling.kim.api.IKimExpression;
import org.integratedmodelling.kim.api.IKimLookupTable;
import org.integratedmodelling.kim.api.IKimModel;
import org.integratedmodelling.kim.api.IKimNamespace;
import org.integratedmodelling.kim.api.IKimObservable;
import org.integratedmodelling.kim.api.IKimRestriction;
import org.integratedmodelling.kim.api.IKimScope;
import org.integratedmodelling.kim.api.IKimSymbolDefinition;
import org.integratedmodelling.kim.api.IServiceCall;
import org.integratedmodelling.kim.api.IValueMediator;
import org.integratedmodelling.kim.model.Kim;
import org.integratedmodelling.kim.model.KimLoader.NamespaceDescriptor;
import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.impl.LiteralImpl;
import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.collections.impl.ParametersImpl;
import org.integratedmodelling.klab.api.data.IGeometry;
import org.integratedmodelling.klab.api.data.IResource;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.data.mediation.ValueMediator;
import org.integratedmodelling.klab.api.data.mediation.impl.Range;
import org.integratedmodelling.klab.api.errormanagement.ICompileNotification;
import org.integratedmodelling.klab.api.exceptions.KIllegalArgumentException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.IObservable;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Resource.Attribute;
import org.integratedmodelling.klab.api.knowledge.SemanticRole;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.BinarySemanticOperator;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.lang.impl.ContextualizableImpl;
import org.integratedmodelling.klab.api.lang.impl.ServiceCallImpl;
import org.integratedmodelling.klab.api.lang.impl.kim.KimAcknowledgementImpl;
import org.integratedmodelling.klab.api.lang.impl.kim.KimClassificationImpl;
import org.integratedmodelling.klab.api.lang.impl.kim.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.impl.kim.KimConceptStatementImpl;
import org.integratedmodelling.klab.api.lang.impl.kim.KimLookupTableImpl;
import org.integratedmodelling.klab.api.lang.impl.kim.KimModelStatementImpl;
import org.integratedmodelling.klab.api.lang.impl.kim.KimNamespaceImpl;
import org.integratedmodelling.klab.api.lang.impl.kim.KimObservableImpl;
import org.integratedmodelling.klab.api.lang.impl.kim.KimStatementImpl;
import org.integratedmodelling.klab.api.lang.impl.kim.KimSymbolDefinitionImpl;
import org.integratedmodelling.klab.api.lang.kim.KimClassification;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConcept.Expression;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimLookupTable;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KimRestriction;
import org.integratedmodelling.klab.api.lang.kim.KimStatement.Scope;
import org.integratedmodelling.klab.api.runtime.rest.INotification;
import org.integratedmodelling.klab.api.services.resources.impl.AttributeImpl;
import org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.Notification.Level;
import org.integratedmodelling.klab.rest.ResourceReference;
import org.integratedmodelling.klab.utilities.Utils;

public class KimAdapter {

	public static KimNamespaceImpl adaptKimNamespace(NamespaceDescriptor ns) {

		IKimNamespace original = ns.getNamespace();
		KimNamespaceImpl ret = new KimNamespaceImpl();
		Utils.Lang.copyStatementData(original, ret);

		ret.setUrn(original.getName());
		for (ICompileNotification notification : ns.getIssues()) {
			// TODO
		}

		ret.setMetadata(Utils.Lang.makeMetadata(ns.getNamespace().getMetadata()));
		ret.setProjectName(ns.getProjectName());
		for (String imported : ns.getNamespace().getImportedNamespaceIds(false)) {
			/*
			 * TODO add symbols. This is done in an awkward way in the original namespace.
			 */
			ret.getImports().put(imported, new ArrayList<>());
		}

		switch (ns.getNamespace().getScope()) {
		case NAMESPACE:
			ret.setScope(Scope.PRIVATE);
			break;
		case PROJECT:
			ret.setScope(Scope.PROJECT_PRIVATE);
			break;
		default:
			ret.setScope(Scope.PUBLIC);
			break;
		}

		for (IKimScope statement : original.getChildren()) {
			ret.getStatements().add(makeStatement(statement, ns.getNamespace().getName()));
		}

		return ret;
	}

	public static KimStatementImpl makeStatement(IKimScope statement, String namespace) {

		KimStatementImpl ret = null;

		if (statement instanceof IKimConceptStatement) {
			ret = adaptConceptStatement((IKimConceptStatement) statement, namespace);
		} else if (statement instanceof IKimModel) {
			ret = adaptModelStatement((IKimModel) statement);
		} else if (statement instanceof IKimSymbolDefinition) {
			ret = adaptSymbolDefinition((IKimSymbolDefinition) statement);
		} else if (statement instanceof IKimAcknowledgement) {
			ret = adaptAcknowledgementStatement((IKimAcknowledgement) statement);
		}

		if (ret != null) {
			for (IKimScope child : statement.getChildren()) {
				ret.getChildren().add(makeStatement(child, namespace));
			}
			return ret;
		}

		throw new KIllegalArgumentException("statement " + statement + " cannot be understood");
	}

	private static KimStatementImpl adaptAcknowledgementStatement(IKimAcknowledgement statement) {

		KimAcknowledgementImpl ret = new KimAcknowledgementImpl();
		Utils.Lang.copyStatementData(statement, ret);

		ret.setDocstring(statement.getDocstring());
		ret.setName(statement.getName());
		ret.setObservable(adaptKimObservable(statement.getObservable()));
		for (IKimObservable state : statement.getStates()) {
			ret.getStates().add(adaptKimObservable(state));
		}
		ret.setUrn(statement.getUrn());

		ret.setUri(ret.getNamespace() + ":" + ret.getName());

		return ret;
	}

	public static KimObservableImpl adaptKimObservable(IKimObservable parsed) {

		KimObservableImpl ret = new KimObservableImpl();
		Utils.Lang.copyStatementData(parsed, ret);

		ret.setAttributeIdentifier(parsed.hasAttributeIdentifier() ? parsed.getValue().toString() : null);
		ret.setValue(parsed.hasAttributeIdentifier() ? null
				: (parsed.getValue() == null ? null : LiteralImpl.of(adapt(parsed.getValue()))));
		ret.setCodeName(parsed.getCodeName());
		ret.setCurrency(parsed.getCurrency());
		ret.setDefaultValue(parsed.getDefaultValue() == null ? null : LiteralImpl.of(adapt(parsed.getDefaultValue())));
		ret.setUrn(parsed.getDefinition());
		ret.setExclusive(parsed.isExclusive());
		ret.setFormalName(parsed.getFormalName());
		ret.setGeneric(parsed.isGeneric());
		ret.setGlobal(parsed.isGlobal());
		ret.setMain(adaptKimConcept(parsed.getMain()));
		ret.setModelReference(parsed.getModelReference());
		ret.setNonSemanticType(
				parsed.getNonSemanticType() == null ? null : Artifact.Type.valueOf(parsed.getNonSemanticType().name()));
		ret.setOptional(parsed.isOptional());
		ret.setRange(parsed.getRange() == null ? null
				: new Range(parsed.getRange().getLowerBound(), parsed.getRange().getUpperBound(),
						parsed.getRange().isLeftBounded(), parsed.getRange().isRightBounded()));
		ret.getResolutionExceptions().addAll(parsed.getResolutionExceptions().stream()
				.map((t) -> Observable.ResolutionException.valueOf(t.name())).collect(Collectors.toSet()));
		ret.setUnit(parsed.getUnit());
		for (org.integratedmodelling.klab.utils.Pair<org.integratedmodelling.kim.api.ValueOperator, Object> vop : parsed
				.getValueOperators()) {
			ret.getValueOperators().add(new PairImpl<ValueOperator, Literal>(
					ValueOperator.valueOf(vop.getFirst().name()), LiteralImpl.of(adapt(vop.getSecond()))));
		}

		ret.setUri(ret.getUri());

		return ret;
	}

	private static Object adapt(Object object) {
		if (object instanceof IKimScope) {
			if (object instanceof IKimConcept) {
				object = adaptKimConcept((IKimConcept) object);
			} else if (object instanceof IKimObservable) {
				object = adaptKimObservable((IKimObservable) object);
			} // TODO continue for all possible literals
		}
		return object;
	}

	public static KimConceptImpl adaptKimConcept(IKimConcept original) {

		KimConceptImpl ret = new KimConceptImpl();
		Utils.Lang.copyStatementData(original, ret);

		ret.setObservable(original.getObservable() == null ? null : adaptKimConcept(original.getObservable()));

		ret.setFundamentalType(original.getFundamentalType() == null ? null
				: SemanticType.valueOf(original.getFundamentalType().name()));
		ret.getType().addAll(
				original.getType().stream().map((t) -> SemanticType.valueOf(t.name())).collect(Collectors.toSet()));
		ret.setAuthority(original.getAuthority());
		ret.setAuthorityTerm(original.getAuthorityTerm());
		ret.setCodeName(original.getCodeName());
		ret.setUrn(original.getDefinition());
		ret.setName(original.getName());
		ret.setNegated(original.isNegated());
		ret.setSemanticModifier(original.getSemanticModifier() == null ? null
				: UnarySemanticOperator.valueOf(original.getSemanticModifier().name()));
		ret.setSemanticRole(original.getDistributedInherent() == null ? null
				: SemanticRole.valueOf(original.getDistributedInherent().name()));
		ret.setTemplate(original.isTemplate());
		ret.setTraitObservable(original.isTraitObservable());
		ret.setExpressionType(Expression.valueOf(original.getExpressionType().name()));

		ret.getRoles().addAll(original.getRoles().stream().map((t) -> adaptKimConcept(t)).collect(Collectors.toList()));
		ret.getTraits()
				.addAll(original.getTraits().stream().map((t) -> adaptKimConcept(t)).collect(Collectors.toList()));

		if (ret.getExpressionType() != Expression.SINGLETON) {
			ret.getOperands().addAll(
					original.getOperands().stream().map((t) -> adaptKimConcept(t)).collect(Collectors.toList()));
		}

		ret.setAdjacent(original.getAdjacent() == null ? null : adaptKimConcept(original.getAdjacent()));
		ret.setComparisonConcept(
				original.getComparisonConcept() == null ? null : adaptKimConcept(original.getComparisonConcept()));
		ret.setCompresent(original.getCompresent() == null ? null : adaptKimConcept(original.getCompresent()));
		ret.setContext(original.getContext() == null ? null : adaptKimConcept(original.getContext()));
		ret.setCooccurrent(original.getCooccurrent() == null ? null : adaptKimConcept(original.getCooccurrent()));
		ret.setCausant(original.getCausant() == null ? null : adaptKimConcept(original.getCausant()));
		ret.setCaused(original.getCaused() == null ? null : adaptKimConcept(original.getCaused()));
		ret.setInherent(original.getInherent() == null ? null : adaptKimConcept(original.getInherent()));
		ret.setMotivation(original.getMotivation() == null ? null : adaptKimConcept(original.getMotivation()));
		ret.setRelationshipSource(
				original.getRelationshipSource() == null ? null : adaptKimConcept(original.getRelationshipSource()));
		ret.setRelationshipTarget(
				original.getRelationshipTarget() == null ? null : adaptKimConcept(original.getRelationshipTarget()));
		ret.setTemporalInherent(
				original.getTemporalInherent() == null ? null : adaptKimConcept(original.getTemporalInherent()));

		ret.setUri(ret.getUri());

		return ret;
	}

	public static KimSymbolDefinitionImpl adaptSymbolDefinition(IKimSymbolDefinition statement) {

		KimSymbolDefinitionImpl ret = new KimSymbolDefinitionImpl();
		Utils.Lang.copyStatementData(statement, ret);

		ret.setDefineClass(statement.getDefineClass());
		ret.setName(statement.getName());
		ret.setValue(LiteralImpl.of(adapt(statement.getValue())));

		return ret;
	}

	public static KimModelStatementImpl adaptModelStatement(IKimModel statement) {

		KimModelStatementImpl ret = new KimModelStatementImpl();
		Utils.Lang.copyStatementData(statement, ret);

		for (IContextualizable contextualizable : statement.getContextualization()) {
			ret.getContextualization().add(adaptContextualization(contextualizable));
		}

		for (IKimObservable dep : statement.getObservables()) {
			ret.getObservables().add(adaptKimObservable(dep));
		}
		for (IKimObservable dep : statement.getDependencies()) {
			ret.getDependencies().add(adaptKimObservable(dep));
		}

		ret.setDocstring(statement.getDocstring());
		ret.setInlineValue(statement.getInlineValue() == null ? null : LiteralImpl.of(statement.getInlineValue()));
		ret.setInstantiator(statement.isInstantiator());
		ret.setInterpreter(statement.isInterpreter());
		ret.setLearningModel(statement.isLearningModel());
		ret.setName(statement.getName());
		ret.setReinterpretingRole(statement.getReinterpretingRole().isEmpty() ? null
				: adaptKimConcept(statement.getReinterpretingRole().get()));
		if (statement.getResourceUrns() != null) {
			ret.getResourceUrns().addAll(statement.getResourceUrns());
		}
		ret.setSemantic(statement.isSemantic());
		ret.setType(statement.isInactive() ? Artifact.Type.VOID
				: Artifact.Type.valueOf(statement.getType().artifactType().name()));
		ret.setUri(ret.getNamespace() + ":" + ret.getName());

		return ret;
	}

	private static Contextualizable adaptContextualization(IContextualizable contextualizable) {

		ContextualizableImpl ret = new ContextualizableImpl();
		Utils.Lang.copyStatementData(contextualizable, ret);

		ret.setAccordingTo(contextualizable.getAccordingTo());
		ret.setClassification(contextualizable.getClassification() == null ? null
				: adaptClassification(contextualizable.getClassification()));
		ret.setCondition(contextualizable.getCondition() == null ? null
				: adaptContextualization(contextualizable.getCondition()));
		ret.setConversion(contextualizable.getConversion() == null ? null
				: new PairImpl<ValueMediator, ValueMediator>(adaptMediator(contextualizable.getConversion().getFirst()),
						adaptMediator(contextualizable.getConversion().getSecond())));
		ret.setEmpty(contextualizable.isEmpty());
		ret.setExpression(
				contextualizable.getExpression() == null ? null : adaptKimExpression(contextualizable.getExpression()));
//        ret.setFinal(contextualizable.isFinal());
		ret.setGeometry(contextualizable.getGeometry() == null ? null : adaptGeometry(contextualizable.getGeometry()));
		ret.setInputs(contextualizable.getInputs().stream()
				.map((c) -> new PairImpl<>(c.getFirst(), Artifact.Type.valueOf(c.getSecond().name())))
				.collect(Collectors.toList()));
		ret.getInteractiveParameters().addAll(contextualizable.getInteractiveParameters());
		ret.setLanguage(contextualizable.getLanguage());
		ret.setLiteral(contextualizable.getLiteral() == null ? null : LiteralImpl.of(contextualizable.getLiteral()));
		ret.setLookupTable(
				contextualizable.getLookupTable() == null ? null : adaptLookupTable(contextualizable.getLookupTable()));
		ret.setMediation(contextualizable.isMediation());
		ret.setMediationTargetId(contextualizable.getMediationTargetId());
		ret.setNegated(contextualizable.isNegated());
		ret.setParameters(contextualizable.getParameters() == null ? null
				: new ParametersImpl<String>(contextualizable.getParameters()));
		ret.setServiceCall(
				contextualizable.getServiceCall() == null ? null : adaptServiceCall(contextualizable.getServiceCall()));
		ret.setTarget(contextualizable.getTarget() == null ? null : adaptObservable(contextualizable.getTarget()));
		ret.setTargetId(contextualizable.getTargetId());
		ret.setType(Contextualizable.Type.valueOf(contextualizable.getType().name()));
		ret.setUrn(contextualizable.getUrn());
		ret.setVariable(contextualizable.isVariable());
		return ret;
	}

	private static ValueMediator adaptMediator(IValueMediator first) {
		// TODO Auto-generated method stub
		return null;
	}

	private static ExpressionCode adaptKimExpression(IKimExpression expression) {
		// TODO Auto-generated method stub
		return null;
	}

	private static KimObservable adaptObservable(IObservable target) {
		IKimObservable parsed = Kim.INSTANCE.declare(target.getDefinition());
		return parsed == null ? null : adaptKimObservable(parsed);
	}

	private static ServiceCall adaptServiceCall(IServiceCall serviceCall) {
		ServiceCallImpl ret = new ServiceCallImpl();
		Utils.Lang.copyStatementData(serviceCall, ret);

		return ret;
	}

	public static Resource adaptResource(ResourceReference resource) {

		ResourceImpl ret = new ResourceImpl();
		ret.setAdapterType(resource.getAdapterType());
		for (IResource.Attribute attribute : resource.getAttributes()) {
			ret.getAttributes().add(adaptAttribute(attribute));
		}
		ret.getCategorizables().addAll(resource.getCategorizables());
		ret.getCodelists().addAll(resource.getCodelists());
		ret.setGeometry(Geometry.create(resource.getGeometry()));
		for (ResourceReference history : resource.getHistory()) {
			ret.getHistory().add(adaptResource(history));
		}
		for (IResource.Attribute attribute : resource.getDependencies()) {
			ret.getInputs().add(adaptAttribute(attribute));
		}
		for (IResource.Attribute attribute : resource.getOutputs()) {
			ret.getOutputs().add(adaptAttribute(attribute));
		}
		ret.getParameters().putAll(resource.getParameters());
		ret.setLocalName(resource.getLocalName());
		ret.setLocalPath(resource.getLocalPath());
		ret.getLocalPaths().addAll(resource.getLocalPaths());
		ret.setProjectName(resource.getProjectName());
		ret.getMetadata().putAll(resource.getMetadata());
		ret.setTimestamp(resource.getResourceTimestamp());
		ret.setType(Artifact.Type.valueOf(resource.getType().name()));
		ret.setUrn(resource.getUrn());
		ret.setVersion(Version.create(resource.getVersion().toString()));

		for (INotification notification : resource.getNotifications()) {
			ret.getNotifications().add(adaptNotification(notification));
		}

		return ret;
	}

	private static Notification adaptNotification(INotification notification) {
		return null;
	}

	private static Attribute adaptAttribute(IResource.Attribute attribute) {
		AttributeImpl ret = new AttributeImpl();

		return ret;
	}

	private static KimLookupTable adaptLookupTable(IKimLookupTable lookupTable) {
		KimLookupTableImpl ret = new KimLookupTableImpl();
		Utils.Lang.copyStatementData(lookupTable, ret);
		// TODO
		return ret;
	}

	private static Geometry adaptGeometry(IGeometry geometry) {
		return GeometryImpl.create(geometry.encode());
	}

	private static KimClassification adaptClassification(IKimClassification classification) {
		KimClassificationImpl ret = new KimClassificationImpl();
		Utils.Lang.copyStatementData(classification, ret);
		// ret
		return ret;
	}

	private static KimConceptStatementImpl adaptConceptStatement(IKimConceptStatement statement, String namespace) {

		KimConceptStatementImpl ret = new KimConceptStatementImpl();
		Utils.Lang.copyStatementData(statement, ret);

		ret.setAbstract(statement.isAbstract());
		ret.setAlias(statement.isAlias());
		ret.setAuthorityDefined(statement.getAuthorityDefined());
		ret.setAuthorityRequired(statement.getAuthorityRequired());
		ret.setDocstring(statement.getDocstring());
		ret.setMacro(statement.isMacro());
		ret.setName(statement.getName());
		ret.setNamespace(namespace);
		ret.getType().addAll(
				statement.getType().stream().map((t) -> SemanticType.valueOf(t.name())).collect(Collectors.toSet()));
		ret.setUpperConceptDefined(statement.getUpperConceptDefined());

		for (ParentConcept p : statement.getParents()) {

			String sc = p.getConnector().name();

			final BinarySemanticOperator op = "NONE".equals(sc) ? null : BinarySemanticOperator.valueOf(sc);
			final List<KimConcept> ops = new ArrayList<>();

			for (IKimConcept parent : p.getConcepts()) {
				ops.add(adaptKimConcept(parent));
			}

			ret.getParents().add(new org.integratedmodelling.klab.api.lang.kim.KimConceptStatement.ParentConcept() {

				@Override
				public BinarySemanticOperator getConnector() {
					return op;
				}

				@Override
				public List<KimConcept> getConcepts() {
					// TODO Auto-generated method stub
					return ops;
				}
			});
		}

		for (ApplicableConcept ac : statement.getAppliesTo()) {

			final KimConcept origin = ac.getOriginalObservable() == null ? null
					: adaptKimConcept(ac.getOriginalObservable());
			final KimConcept source = ac.getSource() == null ? null : adaptKimConcept(ac.getSource());
			final KimConcept target = ac.getTarget() == null ? null : adaptKimConcept(ac.getTarget());

			ret.getAppliesTo()
					.add(new org.integratedmodelling.klab.api.lang.kim.KimConceptStatement.ApplicableConcept() {

						@Override
						public KimConcept getTarget() {
							return target;
						}

						@Override
						public KimConcept getSource() {
							return source;
						}

						@Override
						public KimConcept getOriginalObservable() {
							return origin;
						}
					});
		}

		for (ApplicableConcept ac : statement.getSubjectsLinked()) {

			final KimConcept origin = ac.getOriginalObservable() == null ? null
					: adaptKimConcept(ac.getOriginalObservable());
			final KimConcept source = ac.getSource() == null ? null : adaptKimConcept(ac.getSource());
			final KimConcept target = ac.getTarget() == null ? null : adaptKimConcept(ac.getTarget());

			ret.getSubjectsLinked()
					.add(new org.integratedmodelling.klab.api.lang.kim.KimConceptStatement.ApplicableConcept() {

						@Override
						public KimConcept getTarget() {
							return target;
						}

						@Override
						public KimConcept getSource() {
							return source;
						}

						@Override
						public KimConcept getOriginalObservable() {
							return origin;
						}
					});
		}

		for (IKimConcept c : statement.getEmergenceTriggers()) {
			ret.getEmergenceTriggers().add(adaptKimConcept(c));
		}

		for (IKimConcept c : statement.getObservablesCreated()) {
			ret.getObservablesCreated().add(adaptKimConcept(c));
		}

		for (org.integratedmodelling.klab.utils.Pair<IKimConcept, DescriptionType> c : statement
				.getObservablesDescribed()) {
			ret.getObservablesDescribed().add(new PairImpl<>(adaptKimConcept(c.getFirst()),
					KimConceptStatement.DescriptionType.valueOf(c.getSecond().name())));
		}

		for (IKimConcept c : statement.getQualitiesAffected()) {
			ret.getQualitiesAffected().add(adaptKimConcept(c));
		}

		for (IKimConcept c : statement.getRequiredAttributes()) {
			ret.getRequiredAttributes().add(adaptKimConcept(c));
		}

		for (IKimConcept c : statement.getRequiredExtents()) {
			ret.getRequiredExtents().add(adaptKimConcept(c));
		}

		for (IKimConcept c : statement.getRequiredIdentities()) {
			ret.getRequiredIdentities().add(adaptKimConcept(c));
		}

		for (IKimConcept c : statement.getRequiredRealms()) {
			ret.getRequiredRealms().add(adaptKimConcept(c));
		}

		for (IKimRestriction c : statement.getRestrictions()) {
			ret.getRestrictions().add(adaptKimRestriction(c));
		}

		for (IKimConcept c : statement.getTraitsConferred()) {
			ret.getTraitsConferred().add(adaptKimConcept(c));
		}

		for (IKimConcept c : statement.getTraitsInherited()) {
			ret.getTraitsInherited().add(adaptKimConcept(c));
		}

		return ret;
	}

	private static KimRestriction adaptKimRestriction(IKimRestriction c) {
		// TODO Auto-generated method stub
		return null;
	}

}
