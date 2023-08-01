package org.integratedmodelling.klab.services.resolver.resolution;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.exceptions.KIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Instance;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.knowledge.ModelImpl;

// next-level (actual) resolver that works. Merge into everything when done.
public class ActualResolver {

	/**
	 * Top-level resolution, resolve and merge the graph into the passed one,
	 * returning the coverage. Merging the resulting graph into a main one will
	 * create the final coverage.
	 * 
	 * FIXME this should not take mergeInto but create it and return it, handling
	 * the various cases (models etc.) as additional resolutions merged into it. So
	 * if Instance, make it then resolve its observable and merge; if model, make it
	 * for the observable and merge the resolution of the model. The first condition
	 * should be in each observeXxxx method.
	 * 
	 * @param knowledge
	 * @param scale
	 * @return
	 */
	Coverage resolve(Knowledge knowledge, Scale scale, ContextScope scope, ResolutionGraph mergeInto) {

		if (mergeInto.vertexSet().contains(knowledge)) {
			return mergeInto.getCoverage();
		}

		boolean optional = false;
		ResolutionGraph resolution = null;
		Observable observable = null;

		switch (Knowledge.classify(knowledge)) {
		case CONCEPT:
			observable = Observable.promote((Concept) knowledge);
			resolution = resolveObservable(observable, scale, scope, mergeInto);
			break;
		case INSTANCE:
			optional = true;
			resolution = resolveObservable(((Instance) knowledge).getObservable(), scale, scope, mergeInto);
			break;
		case MODEL:
			resolution = resolveModel((Model) knowledge, scale, scope, mergeInto);
			break;
		case OBSERVABLE:
			observable = (Observable) knowledge;
			optional = observable.isOptional();
			resolution = resolveObservable(observable, scale, scope, mergeInto);
			break;
		case RESOURCE:
//			break;
		default:
			throw new KIllegalStateException("knowledge " + knowledge + " is not resolvable");
		}

		if (resolution.getCoverage().isRelevant()) {
			mergeInto.merge(resolution, observable, LogicalConnector.UNION);
		} else if (!optional) {
			return Coverage.empty();
		}

		return mergeInto.getCoverage();
	}

	/**
	 * Resolve a model's dependencies.
	 * 
	 * @param model
	 * @param scale
	 * @param mergeInto
	 * @return
	 */
	private ResolutionGraph resolveModel(Model model, Scale scale, ContextScope scope, ResolutionGraph mergeInto) {
		ResolutionGraph ret = new ResolutionGraph(model);
		for (Observable dependency : model.getDependencies()) {
			ResolutionGraph resolution = resolveObservable(dependency, scale, scope, mergeInto);
			if (resolution.getCoverage().isRelevant()) {
				ret.merge(resolution, dependency, LogicalConnector.INTERSECTION);
			} else if (dependency.isOptional()) {
				return ret.setEmpty();
			}
		}
		return ret;
	}

	/**
	 * Resolve an observable to a model. If the applicable observation strategy is a
	 * deferral, leave the deferred observable for later.
	 * 
	 * @param promote
	 * @param scale
	 * @param mergeInto
	 * @return
	 */
	private ResolutionGraph resolveObservable(Observable observable, Scale scale, ContextScope scope,
			ResolutionGraph mergeInto) {

		ResolutionGraph ret = new ResolutionGraph(observable);

		for (ObservationStrategy strategy : scope.getService(Reasoner.class).inferStrategies(observable, scope)) {
			switch (strategy.getType()) {
			case DEREIFYING:
				// resolve the deferral, then merge with deferral
				break;
			case DIRECT:
//                for (Model model : queryModels(observable, scope)) {
//                    ResolutionImpl modelResolution = resolveModel(model,
//                            node.newResolution(node.getObservable(), LogicalConnector.UNION));
//                    if (modelResolution.isRelevant()) {
//                        node.resolve(modelResolution, Resolution.Type.DIRECT);
//                    }
//                    if (node.getCoverage().isComplete()) {
//                        break;
//                    }
//                }
				break;
			case RESOLVED:
				break;
			}
		}

		return ret;

	}

	public List<Model> queryModels(Observable observable, ContextScope scope) {

		var ret = new ArrayList<Model>();
		var resources = scope.getService(ResourcesService.class);

		// TODO use and merge all available resource services
		ResourceSet models = resources.queryModels(observable, scope);
		if (!models.isEmpty()) {
//			loadKnowledge(models, scope);
//			for (ResourceSet.Resource urn : models.getResults()) {
//				ret.add(loadModel(resources.model(urn.getResourceUrn(), scope)));
//			}
		}
		return ret;
	}

	private Model loadModel(KimModel statement, Scope scope) {
		ModelImpl model = new ModelImpl();

		return model;
	}

}
