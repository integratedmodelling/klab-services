package org.integratedmodelling.klab.indexing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticRole;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.BinarySemanticOperator;
import org.integratedmodelling.klab.api.lang.SemanticLexicalElement;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.services.UnitService;
import org.integratedmodelling.klab.configuration.Configuration;

/**
 * Defines the acceptable semantic tokens in any given state of the
 * specification of a semantic expression and the lexical context in which it's
 * being specified.
 * 
 * @author Ferd
 *
 */
public class SemanticScope {

	/**
	 * A logical constraint. If not negated, the arguments are required, otherwise
	 * they are prohibited. The argument list may contain IKimConcept.Type,
	 * IConcept, or other constraints. Multiple constraints in the logicalScope
	 * field are in OR; individual arguments in the constraint are in AND.
	 * 
	 * @author Ferd
	 *
	 */
	public static class Constraint {

		public boolean negated = false;
		public Collection<Object> arguments = new HashSet<>();
		public Unit unit;

		private Constraint() {
		}

		public static Constraint not(Object arg) {
			Constraint ret = new Constraint();
			ret.arguments.add(arg);
			ret.negated = true;
			return ret;
		}

		public static Constraint of(Object... objects) {
			Constraint ret = new Constraint();
			for (Object o : objects) {
				ret.arguments.add(o);
			}
			return ret;
		}

		public boolean matches(Concept concept) {
			for (Object o : arguments) {
				if (o instanceof SemanticType
						&& (!concept.is((SemanticType) o) || (negated && concept.is((SemanticType) o)))) {
					return false;
				} else if (o instanceof Constraint && (!((Constraint) o).matches(concept))
						|| (negated && ((Constraint) o).matches(concept))) {
					return false;
				} // TODO continue
			}
			return negated ? false : true;
		}

		public String toString() {
			return (negated ? "<NOT " : "<") + arguments + ">";
		}

		public Constraint compatibleWith(Concept concept) {
			// TODO Auto-generated method stub
			return this;
		}

		public Constraint withDifferentBaseTraitOf(Concept concept) {
			// TODO Auto-generated method stub
			return this;
		}

		public static Constraint compatibleUnit(Unit unit) {
			Constraint ret = new Constraint();
			ret.unit = unit;
			return ret;
		}

	}

	/**
	 * Admitted concept types that can be added in this scope. Empty means
	 * everything's allowed, filled means any of those but nothing else.
	 */
	Set<Constraint> logicalRealm = new HashSet<>();

	/**
	 * Admitted lexical realm for this scope. Calls that fulfill a role which isn't
	 * here are illegal. Empty means everything's allowed, filled means any of those
	 * but nothing else.
	 */
	Set<SemanticRole> lexicalRealm = EnumSet.noneOf(SemanticRole.class);

	String error = null;

	public String toString() {
		if (logicalRealm.isEmpty() && lexicalRealm.isEmpty()) {
			return "[]";
		}

		return lexicalRealm + ", " + logicalRealm;
	}

	public static SemanticScope root() {

		SemanticScope ret = new SemanticScope();
		ret.lexicalRealm.add(SemanticRole.UNARY_OPERATOR);
		ret.lexicalRealm.add(SemanticRole.GROUP_OPEN);
		ret.logicalRealm.add(Constraint.of(SemanticType.OBSERVABLE));
		ret.logicalRealm.add(Constraint.of(SemanticType.PREDICATE));
		return ret;
	}

	public static SemanticScope scope(Concept concept, SemanticExpression context) {

		SemanticScope ret = new SemanticScope();

		if (concept.is(SemanticType.OBSERVABLE)) {

			ret.lexicalRealm.addAll(compatibleModifiers(concept));

			if (concept.is(SemanticType.QUALITY)) {

				/*
				 * add value operators and constrain for those that are already present in the
				 * current lexical scope
				 * 
				 * TODO check if we need to differentiate operators
				 */
				ret.lexicalRealm.add(SemanticRole.VALUE_OPERATOR);

				/*
				 * check for units and constrain for compatibility
				 */
				if (concept.is(SemanticType.MONETARY_VALUE)) {
					ret.lexicalRealm.add(SemanticRole.CURRENCY);
				} else if (concept.is(SemanticType.EXTENSIVE_PROPERTY) || concept.is(SemanticType.INTENSIVE_PROPERTY)) {
					Unit baseUnit = Configuration.INSTANCE.getService(UnitService.class).getDefaultUnitFor(concept);
					if (baseUnit != null) {
						ret.lexicalRealm.add(SemanticRole.UNIT);
						ret.logicalRealm.add(Constraint.compatibleUnit(baseUnit));
					}
				} else if (concept.is(SemanticType.NUMEROSITY)) {
					ret.lexicalRealm.add(SemanticRole.UNIT);
					ret.logicalRealm.add(Constraint
							.compatibleUnit(Configuration.INSTANCE.getService(UnitService.class).getUnit("1")));
				}
			}

		} else {
			ret.lexicalRealm.add(SemanticRole.UNARY_OPERATOR);
			ret.logicalRealm.add(Constraint.of(SemanticType.PREDICATE).withDifferentBaseTraitOf(concept));
			ret.logicalRealm.add(Constraint.of(SemanticType.OBSERVABLE).compatibleWith(concept));
		}

		return ret;
	}

	private static Collection<SemanticRole> compatibleModifiers(Concept concept) {
		List<SemanticRole> ret = new ArrayList<>();
		for (SemanticLexicalElement modifier : SemanticLexicalElement.values()) {
			if (modifier.role != null) {
				for (SemanticType type : modifier.applicable) {
					if (concept.is(type)) {
						ret.add(modifier.role);
						break;
					}
				}
			}
		}
		return ret;

	}

	public static SemanticScope scope(String syntacticElement, SemanticExpression context) {

		SemanticScope ret = new SemanticScope();

		switch (syntacticElement) {
		case "(":
			// no change to the scope of the inner observable
			ret = context.getCurrent().getScope();
			break;
		case "in":
			// collect observable, set to currency or unit
			break;
		case "per":
			// collect observable, set to numerosity
			break;
		default:
			// unit or currency: check context, validate, then return empty scope
		}

		return ret;
	}

	public static SemanticScope scope(BinarySemanticOperator op, SemanticExpression context) {

		SemanticScope ret = new SemanticScope();

		switch (op) {
		case FOLLOWS:
			ret.logicalRealm.add(Constraint.of(SemanticType.EVENT));
			ret.lexicalRealm.add(SemanticRole.GROUP_OPEN);
			break;
		case INTERSECTION:
			// must intersect same type
			ret.logicalRealm.addAll(context.getCurrent().getScope().getAdmittedLogicalInput());
			ret.lexicalRealm.add(SemanticRole.GROUP_OPEN);
			break;
		case UNION:
			// must intersect same type
			ret.logicalRealm.addAll(context.getCurrent().getScope().getAdmittedLogicalInput());
			ret.lexicalRealm.add(SemanticRole.GROUP_OPEN);
			break;
		}

		return ret;
	}

	public static SemanticScope scope(ValueOperator op, SemanticExpression context) {

		SemanticScope ret = new SemanticScope();

		switch (op) {
		case AVERAGED:
		case TOTAL:
		case SUMMED:
			// no further input
			break;
		case BY:
			ret.logicalRealm.add(Constraint.of(SemanticType.ABSTRACT, SemanticType.CLASS));
			ret.logicalRealm.add(Constraint.of(SemanticType.COUNTABLE));
			break;
		case DOWN_TO:
			ret.logicalRealm.add(Constraint.of(Constraint.not(SemanticType.ABSTRACT), SemanticType.CLASS));
			ret.lexicalRealm.add(SemanticRole.INLINE_VALUE);
			break;
		case GREATEREQUAL:
		case GREATER:
		case LESS:
		case LESSEQUAL:
		case MINUS:
		case OVER:
		case PLUS:
		case SAMEAS:
		case IS:
		case TIMES:
		case WITHOUT:
			ret.lexicalRealm.add(SemanticRole.INLINE_VALUE);
			break;
		case WHERE:
			ret.lexicalRealm.add(SemanticRole.GROUP_OPEN);
			break;
		}

		return ret;
	}

	public static SemanticScope scope(SemanticLexicalElement role, SemanticExpression context) {

		SemanticScope ret = new SemanticScope();
		// always possible to scope for a complex observable
		ret.lexicalRealm.add(SemanticRole.GROUP_OPEN);
		for (SemanticType type : role.argument) {
			ret.logicalRealm.add(Constraint.of(type));
		}
		return ret;
	}

	public static SemanticScope scope(UnarySemanticOperator role, SemanticExpression context) {

		SemanticScope ret = new SemanticScope();

		// always possible to scope for a complex observable
		ret.lexicalRealm.add(SemanticRole.GROUP_OPEN);

		switch (role) {
		case CHANGE:
			ret.logicalRealm.add(Constraint.of(SemanticType.PROCESS));
			break;
		case CHANGED:
			ret.logicalRealm.add(Constraint.of(SemanticType.QUALITY));
			break;
		case COUNT:
			ret.logicalRealm.add(Constraint.of(SemanticType.COUNTABLE));
			break;
		case DISTANCE:
			ret.logicalRealm.add(Constraint.of(SemanticType.COUNTABLE));
			break;
		case LEVEL:
			ret.logicalRealm.add(Constraint.of(SemanticType.QUALITY));
			break;
		case MAGNITUDE:
			ret.logicalRealm.add(Constraint.of(SemanticType.QUALITY));
			break;
		case MONETARY_VALUE:
			ret.logicalRealm.add(Constraint.of(SemanticType.OBSERVABLE));
			break;
		case NOT:
			ret.logicalRealm.add(Constraint.of(SemanticType.DENIABLE));
			break;
		case OCCURRENCE:
			ret.logicalRealm.add(Constraint.of(SemanticType.COUNTABLE));
			ret.logicalRealm.add(Constraint.of(SemanticType.QUALITY));
			ret.logicalRealm.add(Constraint.of(SemanticType.PROCESS));
			break;
		case PERCENTAGE:
		case PROPORTION:
			ret.logicalRealm.add(Constraint.of(SemanticType.QUALITY));
			break;
		case PRESENCE:
			ret.logicalRealm.add(Constraint.of(SemanticType.COUNTABLE));
			break;
		case PROBABILITY:
			ret.logicalRealm.add(Constraint.of(SemanticType.EVENT));
			break;
		case RATE:
			ret.logicalRealm.add(Constraint.of(SemanticType.QUALITY));
			break;
		case RATIO:
			ret.logicalRealm.add(Constraint.of(SemanticType.QUALITY));
			break;
		case TYPE:
			ret.logicalRealm.add(Constraint.of(SemanticType.CLASS));
			break;
		case UNCERTAINTY:
			ret.logicalRealm.add(Constraint.of(SemanticType.QUALITY));
			break;
		case VALUE:
			ret.logicalRealm.add(Constraint.of(SemanticType.OBSERVABLE));
			break;
		default:
			break;

		}

		return ret;
	}

	/**
	 * Check for admissibility of the passed token; if not admitted, set the error
	 * and return false.
	 * 
	 * @param token
	 * @return
	 */
	public boolean isAdmitted(Object token, SemanticExpression context) {

		boolean ret = false;

		if (token instanceof Concept) {

			for (Constraint c : this.logicalRealm) {
				if (c.matches((Concept) token)) {
					ret = true;
				}
			}

		} else if (token instanceof ValueOperator) {
			ret = this.lexicalRealm.contains(SemanticRole.VALUE_OPERATOR)
					&& !context.collect(SemanticRole.VALUE_OPERATOR).contains(token);
		} else if (token instanceof SemanticLexicalElement) {
			ret = this.lexicalRealm.contains(((SemanticLexicalElement) token).role)
					&& !context.collect(SemanticRole.SEMANTIC_MODIFIER).contains(token);
		} else if (token instanceof UnarySemanticOperator) {
			ret = this.lexicalRealm.contains(SemanticRole.UNARY_OPERATOR)
					&& context.collect(SemanticRole.UNARY_OPERATOR).isEmpty();
		} else if (token instanceof BinarySemanticOperator) {
			ret = this.lexicalRealm.contains(SemanticRole.BINARY_OPERATOR);
		} else if (token instanceof String) {
			switch ((String) token) {
			case "(":
				ret = this.lexicalRealm.contains(SemanticRole.GROUP_OPEN);
				break;
			case ")":
				ret = context.getCurrent().getGroupParent() != null;
				break;
			case "in":
				Set<Object> obs = context.collect(SemanticRole.OBSERVABLE);
				if (!obs.isEmpty()) {
					if (((Concept) obs.iterator().next()).is(SemanticType.MONETARY_VALUE)) {
						ret = this.lexicalRealm.contains(SemanticRole.CURRENCY)
								&& context.collect(SemanticRole.CURRENCY).isEmpty();
					} else {
						ret = this.lexicalRealm.contains(SemanticRole.UNIT)
								&& context.collect(SemanticRole.UNIT).isEmpty();
					}
				}
				break;
			case "per":
				ret = this.lexicalRealm.contains(SemanticRole.DISTRIBUTED_UNIT);
				break;
			default:

				/*
				 * TODO must be under unit, currency or operator value; validate as required
				 * based on context
				 */
				if (context.getCurrent().isAs(SemanticRole.UNIT)) {
					// validate against property (not numerosity). TODO may want a constraint for
					// base unit
				} else if (context.getCurrent().isAs(SemanticRole.CURRENCY)) {
					// validate against monetary value
				} else if (context.getCurrent().isAs(SemanticRole.DISTRIBUTED_UNIT)) {
					// validate against numerosity, must be unitless. TODO may want a constraint for
					// base unit
				} else if (context.getCurrent().isAs(SemanticRole.INLINE_VALUE)) {
					// validate against operator and observable. TODO may want a type constraint
				}
				break;
			}
		}

		if (!ret && error == null) {
			// catch-all
			this.error = "token " + token + " is illegal in this position of a semantic expression";
		}

		return ret;
	}

	public String getErrorAndReset() {
		String ret = error;
		this.error = null;
		return ret;
	}

	public Collection<SemanticRole> getAdmittedLexicalInput() {
		return lexicalRealm;
	}

	public Collection<Constraint> getAdmittedLogicalInput() {
		return logicalRealm;
	}

}