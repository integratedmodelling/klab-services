//package org.integratedmodelling.klab.rest;
//
//import java.util.ArrayList;
//import java.util.List;
//
//import org.integratedmodelling.klab.api.data.mediation.NumericRange;
//import org.integratedmodelling.klab.api.knowledge.Artifact;
//
//public class ServicePrototype {
//
//	public static class Argument {
//
//		private String name;
//		private boolean required;
//		private boolean isFinal;
//		private Artifact.Type type;
//		private NumericRange valueRange;
//		private String defaultValue;
//		private String description;
//
//		public String getName() {
//			return name;
//		}
//
//		public void setName(String name) {
//			this.name = name;
//		}
//
//		public boolean isRequired() {
//			return required;
//		}
//
//		public void setRequired(boolean required) {
//			this.required = required;
//		}
//
//		public Artifact.Type getType() {
//			return type;
//		}
//
//		public void setType(Artifact.Type type) {
//			this.type = type;
//		}
//
//		public NumericRange getValueRange() {
//			return valueRange;
//		}
//
//		public void setValueRange(NumericRange valueRange) {
//			this.valueRange = valueRange;
//		}
//
//		public String getDefaultValue() {
//			return defaultValue;
//		}
//
//		public void setDefaultValue(String defaultValue) {
//			this.defaultValue = defaultValue;
//		}
//
//		public String getDescription() {
//			return description;
//		}
//
//		public void setDescription(String description) {
//			this.description = description;
//		}
//
//		public void setFinal(boolean final1) {
//			this.isFinal = final1;
//		}
//
//		public boolean isFinal() {
//			return this.isFinal;
//		}
//
//	}
//
//	private String name;
//	private List<Argument> arguments = new ArrayList<>();
//	private String description;
//
//	public String getName() {
//		return name;
//	}
//
//	public void setName(String name) {
//		this.name = name;
//	}
//
//	public List<Argument> getArguments() {
//		return arguments;
//	}
//
//	public void setArguments(List<Argument> arguments) {
//		this.arguments = arguments;
//	}
//
//	public String getDescription() {
//		return description;
//	}
//
//	public void setDescription(String description) {
//		this.description = description;
//	}
//
//	/*
//	 * bits of functionality carefully not named "get"-anything.
//	 */
//	public Argument findArgument(String argument) {
//		for (Argument arg : arguments) {
//			if (arg.getName().equals(argument))  {
//				return arg;
//			}
//		}
//		return null;
//	}
//
//}
