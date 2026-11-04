package org.integratedmodelling.klab.services.runtime;
import org.integratedmodelling.klab.api.geometry.Geometry;import org.integratedmodelling.klab.api.digitaltwin.Scheduler;import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
/** Package-access bridge to the real compiled root invocation; no authority construction. */
public final class ModeledMembershipInvocation { public static boolean execute(CompiledDataflow flow,Geometry support,ServiceContextScope scope){return flow.executeRoot(support,Scheduler.Event.initialization(),scope);} }
