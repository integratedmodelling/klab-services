package org.integratedmodelling.klab.api.lang.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.documentation.Documentation;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.lang.Prototype;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.utils.Utils;

public class PrototypeImpl implements Prototype {

	private static final long serialVersionUID = -9168391783660976848L;

	public static class ArgumentImpl implements Prototype.Argument {

		private static final long serialVersionUID = -6573430853944135837L;
		private String name;
		private String shortName;
		private String description = "";
		private boolean option;
		private boolean optional;
		private boolean isFinal;
		private List<Type> type = new ArrayList<>();
		private boolean artifact;
		private boolean expression;
		private boolean parameter;
		private Literal defaultValue = null;
		private Set<String> enumValues = new HashSet<>();
		private String label = null;
		private String unit = null;
		private boolean isConst;

		public ArgumentImpl() {
		}

		public boolean isConst() {
			return isConst;
		}

		public void setConst(boolean isConst) {
			this.isConst = isConst;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getDescription() {
			return description;
		}

		@Override
		public boolean isOption() {
			return option;
		}

		@Override
		public boolean isOptional() {
			return optional;
		}

		@Override
		public List<Type> getType() {
			return type;
		}

		@Override
		public Set<String> getEnumValues() {
			return enumValues;
		}

		@Override
		public Literal getDefaultValue() {
			return defaultValue;
		}

		@Override
		public String getShortName() {
			// if (shortName == null) {
			// shortName = computeShortName();
			// }
			return shortName;
		}

		public String computeShortName() {
			// TODO Auto-generated method stub
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public void setShortName(String shortName) {
			this.shortName = shortName;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public void setOption(boolean option) {
			this.option = option;
		}

		public void setOptional(boolean optional) {
			this.optional = optional;
		}

		public void setType(List<Type> type) {
			this.type = type;
		}

		public void setDefaultValue(Literal defaultValue) {
			this.defaultValue = defaultValue;
		}

		public void setEnumValues(Set<String> enumValues) {
			this.enumValues = enumValues;
		}

		@Override
		public boolean isFinal() {
			return isFinal;
		}

		@Override
		public boolean isArtifact() {
			return artifact;
		}

		public void setFinal(boolean isFinal) {
			this.isFinal = isFinal;
		}

		public void setArtifact(boolean artifact) {
			this.artifact = artifact;
		}

		@Override
		public String getLabel() {
			return label == null ? name : label;
		}

		public void setLabel(String label) {
			this.label = label;
		}

		@Override
		public boolean isParameter() {
			return parameter;
		}

		public void setParameter(boolean parameter) {
			this.parameter = parameter;
		}

		@Override
		public boolean isExpression() {
			return expression;
		}

		public void setExpression(boolean expression) {
			this.expression = expression;
		}

		@Override
		public String getUnit() {
			return this.unit;
		}

		public void setUnit(String unit) {
			this.unit = unit;
		}

	}

	private String name;
	// stable ordering reflecting that of the KDL arguments
	private Map<String, ArgumentImpl> arguments = new LinkedHashMap<>();
	private String description;
	private Class<?> implementation;
	private List<Type> type = new ArrayList<>();
	private Geometry geometry;
	private boolean distributed;
	private boolean contextualizer;
	private boolean filter;
	private String label = null;
	private List<ArgumentImpl> exports = new ArrayList<>();
	private List<ArgumentImpl> imports = new ArrayList<>();
	private List<ArgumentImpl> inputAnnotations = new ArrayList<>();
	private List<ArgumentImpl> outputAnnotations = new ArrayList<>();
	private boolean isConst;
	private boolean reentrant;
	private String executorMethod;

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setArguments(Map<String, ArgumentImpl> arguments) {
		this.arguments = arguments;
	}

	public void setType(List<Type> type) {
		this.type = type;
	}

	public void setGeometry(Geometry geometry) {
		this.geometry = geometry;
	}

	public void setDistributed(boolean distributed) {
		this.distributed = distributed;
	}

	public void setContextualizer(boolean contextualizer) {
		this.contextualizer = contextualizer;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public List<Type> getType() {
		return type;
	}

	@Override
	public Argument getArgument(String argumentId) {
		return arguments.get(argumentId);
	}

	public Map<String, ArgumentImpl> getArguments() {
		return arguments;
	}

	@Override
	public List<Prototype.Argument> listArguments() {
		return new ArrayList<>(arguments.values());
	}

	@Override
	public List<PairImpl<String, Level>> validate(ServiceCall function) {
		List<PairImpl<String, Level>> ret = new ArrayList<>();
		// validate existing arguments
		for (String arg : function.getParameters().keySet()) {
			ArgumentImpl argument = arguments.get(arg);
			if (argument == null) {
				ret.add(new PairImpl<>(name + ": argument " + arg + " is not recognized", Level.SEVERE));
			} else {
				Object val = function.getParameters().get(arg);
//				if ((val = classify(val, argument)) == null) {
//					ret.add(Pair.of(name + ": argument " + arg + " is of incompatible type: "
//							+ (argument.getType() == Type.ENUM
//									? ("one of " + Arrays.toString(
//											argument.enumValues.toArray(new String[argument.enumValues.size()])))
//									: argument.getType().name().toLowerCase())
//							+ " expected", Level.SEVERE));
//				}
			}
		}
		// ensure that all mandatory args are there
		for (ArgumentImpl arg : arguments.values()) {
			if (!arg.isOptional() && !function.getParameters().containsKey(arg.name)) {
//				ret.add(Pair.of(name + ": mandatory argument " + arg.name + " was not passed", Level.SEVERE));
			}
		}
		// TODO does not check that invalid parameters are NOT passed. At the moment it
		// would break a lot of code so
		// do it when things are calm.
		return ret;
	}

	/*
	 * Validate the passed object as the type requested and return it (or its
	 * transformation if allowed) if valid; return null otherwise.
	 */
	private Object classify(Object val, ArgumentImpl argument, int typeIndex) {
		if (val == null) {
			return true;
		}
		switch (argument.getType().get(typeIndex)) {
		case ANNOTATION:
			break;
		case BOOLEAN:
			if (!(val instanceof Boolean)) {
				return null;
			}
			break;
		case CONCEPT:
			// IConceptDescriptor cd = Kim.INSTANCE.getConceptDescriptor(val.toString());
			// if (cd == null) {
			// return false;
			// }
			break;
		case ENUM:
			if (argument.enumValues == null || !argument.enumValues.contains(val.toString())) {
				return null;
			}
			break;
		case LIST:
			if (!(val instanceof List)) {
				List<Object> ret = new ArrayList<>();
				ret.add(val);
				val = ret;
			}
			break;
		case NUMBER:
			if (!(val instanceof Number)) {
				return null;
			}
			break;
		case EXTENT:
		case SPATIALEXTENT:
		case TEMPORALEXTENT:
		case OBJECT:
			// TODO must be a map or table literal with proper type specs, or a symbol
			// defined as
			// such, if passed through k.IM.
			break;
		case PROCESS:
			break;
		case RANGE:
			break;
		case TEXT:
			if (!(val instanceof String)) {
				return null;
			}
			break;
		case VALUE:
			break;
		case VOID:
			// shouldn't happen
			break;
		default:
			break;

		}
		return val;
	}

	@Override
	public String getSynopsis(Integer... flags) {

		if (flags != null) {
			boolean tags = false;
			for (Integer flag : flags) {
				if (flag == Documentation.DOC_HTMLTAGS) {
					tags = true;
				}
			}

			String ret = Utils.Strings.justifyLeft(
					Utils.Strings.pack(
							description == null || description.isEmpty() ? "No description provided." : description),
					80) + (tags ? "<p>" : "\n\n");
			if (tags) {
				ret += "<dl>";
			}
			for (String argument : arguments.keySet()) {
				Argument arg = arguments.get(argument);
				ret += "  " + (tags ? "<dt>" : "") + (arg.isOptional() ? "" : "* ") + argument + (tags ? "</dt>" : "")
						+ (tags ? "" : ":\n");
				String description = Utils.Strings.pack(
						arg.getDescription() == null || arg.getDescription().isEmpty() ? "No description provided."
								: arg.getDescription() + "\n");
				ret += tags ? ("<dd>" + description + "</dd>")
						: Utils.Strings.indent(Utils.Strings.justifyLeft(description, 50), 15);
				ret += (tags ? "" : "\n");
			}
			if (tags) {
				ret += "</dl>";
			}

			if (imports.size() > 0) {
				ret += "\n\n" + (tags ? "<p>" : "");
				ret += "Imports (match dependency names):" + (tags ? "</p>" : "") + "\n\n";
				if (tags) {
					ret += "<dl>";
				}
				for (Argument arg : imports) {
					ret += "  " + (tags ? "<dt>" : "") + (arg.isOptional() ? "" : "* ") + arg.getName()
							+ (tags ? "</dt>" : "") + (tags ? "" : ":\n");
					String description = Utils.Strings.pack(
							arg.getDescription() == null || arg.getDescription().isEmpty() ? "No description provided."
									: arg.getDescription());
					ret += tags ? ("<dd>" + description + "</dd>")
							: Utils.Strings.indent(Utils.Strings.justifyLeft(description, 50), 15);
					ret += (tags ? "" : "\n");
				}
				if (tags) {
					ret += "</dl>";
				}
			}

			if (exports.size() > 0) {
				ret += "\n\n" + (tags ? "<p>" : "");
				ret += "Exports (match output names):" + (tags ? "</p>" : "") + "\n\n";
				if (tags) {
					ret += "<dl>";
				}
				for (Argument arg : exports) {
					ret += "  " + (tags ? "<dt>" : "") + (arg.isOptional() ? "" : "* ") + arg.getName()
							+ (tags ? "</dt>" : "") + (tags ? "" : ":\n");
					String description = Utils.Strings.pack(
							arg.getDescription() == null || arg.getDescription().isEmpty() ? "No description provided."
									: arg.getDescription());
					ret += tags ? ("<dd>" + description + "</dd>")
							: Utils.Strings.indent(Utils.Strings.justifyLeft(description, 50), 15);
					ret += (tags ? "" : "\n");
				}
				if (tags) {
					ret += "</dl>";
				}
			}

			if (inputAnnotations.size() > 0) {
				ret += "\n\n" + (tags ? "<p>" : "");
				ret += "Annotation tags for inputs:" + (tags ? "</p>" : "") + "\n\n";
				if (tags) {
					ret += "<dl>";
				}
				for (Argument arg : outputAnnotations) {
					ret += "  " + (tags ? "<dt>" : "") + (arg.isOptional() ? "" : "* ") + arg.getName()
							+ (tags ? "</dt>" : "") + (tags ? "" : ":\n");
					String description = Utils.Strings.pack(
							arg.getDescription() == null || arg.getDescription().isEmpty() ? "No description provided."
									: arg.getDescription());
					ret += tags ? ("<dd>" + description + "</dd>")
							: Utils.Strings.indent(Utils.Strings.justifyLeft(description, 50), 15);
					ret += (tags ? "" : "\n");
				}
				if (tags) {
					ret += "</dl>";
				}
			}

			if (outputAnnotations.size() > 0) {
				ret += "\n\n" + (tags ? "<p>" : "");
				ret += "Annotation tags for outputs:" + (tags ? "</p>" : "") + "\n\n";
				if (tags) {
					ret += "<dl>";
				}
				for (Argument arg : outputAnnotations) {
					ret += "  " + (tags ? "<dt>" : "") + (arg.isOptional() ? "" : "* ") + arg.getName()
							+ (tags ? "</dt>" : "") + (tags ? "" : ":\n");
					String description = Utils.Strings.pack(
							arg.getDescription() == null || arg.getDescription().isEmpty() ? "No description provided."
									: arg.getDescription());
					ret += tags ? ("<dd>" + description + "</dd>")
							: Utils.Strings.indent(Utils.Strings.justifyLeft(description, 50), 15);
					ret += (tags ? "" : "\n");
				}
				if (tags) {
					ret += "</dl>";
				}
			}

			return ret;

		}
		return getShortSynopsis();
	}

	@Override
	public String getShortSynopsis() {

		String ret = getName();

		for (ArgumentImpl arg : arguments.values()) {
			if (arg.isOptional()) {
				ret += " [" + arg.getName() + "=" + arg.type + printEnumValues(arg) + "]";
			} else {
				ret += " " + arg.name + "=" + arg.type + printEnumValues(arg);
			}
		}

		return ret;
	}

	private String printEnumValues(ArgumentImpl arg) {
		String ret = "";
		if (arg.type.get(0) == Type.ENUM) {
			ret += "(";
			for (String s : arg.enumValues) {
				ret += (ret.length() == 1 ? "" : ",") + s;
			}
			ret += ")";
		}
		return ret;
	}

	@Override
	public Class<?> getExecutorClass() {
		return implementation;
	}

	@Override
	public boolean isDistributed() {
		return distributed;
	}

	@Override
	public Geometry getGeometry() {
		return geometry;
	}

	@Override
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public boolean isContextualizer() {
		return contextualizer;
	}

	@Override
	public List<Argument> listImports() {
		return new Utils.Casts<ArgumentImpl, Argument>().cast(imports);
	}

	@Override
	public List<Argument> listExports() {
		return new Utils.Casts<ArgumentImpl, Argument>().cast(exports);
	}

	public List<Argument> getImports() {
		return new Utils.Casts<ArgumentImpl, Argument>().cast(imports);
	}

	public List<Argument> getExports() {
		return new Utils.Casts<ArgumentImpl, Argument>().cast(exports);
	}

	public void setImports(List<ArgumentImpl> arguments) {
		this.imports = arguments;
	}

	public void setExports(List<ArgumentImpl> arguments) {
		this.exports = arguments;
	}

	@Override
	public Collection<Argument> listImportAnnotations() {
		return new Utils.Casts<ArgumentImpl, Argument>().cast(inputAnnotations);
	}

	@Override
	public boolean isFilter() {
		return filter;
	}

	@Override
	public Collection<Argument> listExportAnnotations() {
		return new Utils.Casts<ArgumentImpl, Argument>().cast(outputAnnotations);
	}

	@Override
	public boolean isFinal() {
		return isConst;
	}

	public Class<?> getImplementation() {
		return implementation;
	}

	public void setImplementation(Class<?> implementation) {
		this.implementation = implementation;
	}

	public List<ArgumentImpl> getInputAnnotations() {
		return inputAnnotations;
	}

	public void setInputAnnotations(List<ArgumentImpl> inputAnnotations) {
		this.inputAnnotations = inputAnnotations;
	}

	public List<ArgumentImpl> getOutputAnnotations() {
		return outputAnnotations;
	}

	public void setOutputAnnotations(List<ArgumentImpl> outputAnnotations) {
		this.outputAnnotations = outputAnnotations;
	}

	public boolean isConst() {
		return isConst;
	}

	public void setConst(boolean isConst) {
		this.isConst = isConst;
	}

	public boolean isReentrant() {
		return reentrant;
	}

	public void setReentrant(boolean reentrant) {
		this.reentrant = reentrant;
	}

	public void setFilter(boolean filter) {
		this.filter = filter;
	}

	public String getExecutorMethod() {
		return executorMethod;
	}

	public void setExecutorMethod(String executorMethod) {
		this.executorMethod = executorMethod;
	}

}
