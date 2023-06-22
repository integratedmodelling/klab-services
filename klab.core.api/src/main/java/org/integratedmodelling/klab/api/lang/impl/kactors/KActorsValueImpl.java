package org.integratedmodelling.klab.api.lang.impl.kactors;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Arguments;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Call;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue;

public class KActorsValueImpl extends KActorsCodeStatementImpl implements KActorsValue {

	private static final long serialVersionUID = 8055708952216648277L;

	public static class ConstructorImpl implements Constructor {

		private static final long serialVersionUID = -6189776643526310594L;

		private String classpath;
		private String classname;
		private String component;
		private Arguments arguments;

		@Override
		public String getClasspath() {
			return this.classpath;
		}

		@Override
		public String getClassname() {
			return this.classname;
		}

		@Override
		public String getComponent() {
			return this.component;
		}

		@Override
		public Arguments getArguments() {
			return this.arguments;
		}

		public void setClasspath(String classpath) {
			this.classpath = classpath;
		}

		public void setClassname(String classname) {
			this.classname = classname;
		}

		public void setComponent(String component) {
			this.component = component;
		}

		public void setArguments(Arguments arguments) {
			this.arguments = arguments;
		}

	}

	private ValueType type;
	private ExpressionType expressionType;
	private Literal statedValue;
	private boolean exclusive;
	private KActorsValue trueCase;
	private KActorsValue falseCase;
	private boolean deferred;
	private List<Call> callChain = new ArrayList<>();
	private DataType cast;
	private Constructor constructor;

	@Override
	public ValueType getType() {
		return this.type;
	}

	@Override
	public ExpressionType getExpressionType() {
		return this.expressionType;
	}

	@Override
	public Literal getStatedValue() {
		return this.statedValue;
	}

	@Override
	public <T> T as(Class<? extends T> cls) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isExclusive() {
		return this.exclusive;
	}

	@Override
	public KActorsValue getTrueCase() {
		return this.trueCase;
	}

	@Override
	public KActorsValue getFalseCase() {
		return this.falseCase;
	}

	@Override
	public boolean isDeferred() {
		return this.deferred;
	}

	@Override
	public List<Call> getCallChain() {
		return this.callChain;
	}

	@Override
	public DataType getCast() {
		return this.cast;
	}

	public void setType(ValueType type) {
		this.type = type;
	}

	public void setExpressionType(ExpressionType expressionType) {
		this.expressionType = expressionType;
	}

	public void setStatedValue(Literal statedValue) {
		this.statedValue = statedValue;
	}

	public void setExclusive(boolean exclusive) {
		this.exclusive = exclusive;
	}

	public void setTrueCase(KActorsValue trueCase) {
		this.trueCase = trueCase;
	}

	public void setFalseCase(KActorsValue falseCase) {
		this.falseCase = falseCase;
	}

	public void setDeferred(boolean deferred) {
		this.deferred = deferred;
	}

	public void setCallChain(List<Call> callChain) {
		this.callChain = callChain;
	}

	public void setCast(DataType cast) {
		this.cast = cast;
	}

	@Override
	public Constructor getConstructor() {
		return constructor;
	}

	public void setConstructor(Constructor constructor) {
		this.constructor = constructor;
	}

}
