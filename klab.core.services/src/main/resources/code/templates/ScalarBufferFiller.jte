@import org.integratedmodelling.klab.runtime.computation.TemplateCodeInfo
@import gg.jte.support.ForSupport
@param TemplateCodeInfo codeInfo

import java.util.function.LongConsumer;
import org.integratedmodelling.klab.api.data.Storage
import org.integratedmodelling.klab.api.digitaltwin.Scheduler
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.runtime.computation.ExpressionBase
import org.integratedmodelling.klab.api.scope.ContextScope
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.api.geometry.Geometry;
@for(var localImport : codeInfo.getAdditionalImports()) {
    ${localImport}
@endfor

/**
 * Scalar buffer-based processing with "local" parallelism. Default for the local engines and OK for distributed
 * computation without involving clustered engines with Spark or other infrastructure.
 */
    class ${codeInfo.getClassName()} extends ExpressionBase {

    @for(var field : codeInfo.getFieldDeclarations())
      ${field}
    @endfor

      <%--  Constructor --%>
      ${codeInfo.getClassName()} (ContextScope scope, Observation self @for(var param : codeInfo.getConstructorArguments()), ${param}@endfor ) {
    super ( scope, self )
    @for(var param : codeInfo.getConstructorInitializationStatements())
    ${param}
    @endfor
    }

    @Override
    boolean run ( Map < String , Storage.Scanner > scanners, Scheduler.Event event, ContextScope scope ) {

    <%-- Catch any exception --%>
    try {

    <%-- body initialization. Must include all buffer creation + the bufferSets as a transposed list of shards, \
       ensuring that each thread has the same topology--%>
    <%-- ALL SCANNERS should be defined here as variables taken from the scanners map, with a def that explicitly
        casts them to their actual type. DO NOT FORGET THE CAST! --%>
    <%-- selfBuffer MUST be defined here --%>
    <%-- Example:
          def elevationBuffer = (Storage.DoubleScanner) scanners.get("elevation")
          def slopeBuffer = (Storage.DoubleScanner) scanners.get("slope")
          def selfBuffer = (Storage.DoubleScanner) scanners.get("self")
    --%>
   <%-- Also add initialization for observation wrappers for all observations mentioned as
        non-scalars (nameObs) and for scope, time, space etc. as needed. --%>
   <%-- Example:
            def elevationObs = new ObservationWrapper(__elevation, event)
   --%>
   @for(var param : codeInfo.getBodyInitializationStatements())
     ${param}
   @endfor

    <%-- Here also go any local space/time proxies and non-contextual unit treatment --%>
    @for(var localVar : codeInfo.getLocalVariableDeclarations())
        ${localVar}
    @endfor

   <%-- main iteration. All scanners must be in sync --%>

    var n = selfBuffer.size()
    for (long i = 0; i < n; i++) {
    <%-- here is the actual preprocessed expression content --%>
    <%--extract all needed scalar dependencies as non-boxing scalars from shards --%>
    <%--e.g. double elevation = elevationScanner.get() --%>
    <%-- Here also go any local space/time proxies and contextual unit treatment --%>
    @for(var localVar : codeInfo.getLoopVariableAssignments())
        ${localVar}
    @endfor

   selfBuffer.add (
    /* START COMPILED SCALAR CODE */
    @for(var codeBlock : codeInfo.getMainCodeBlocks())
       ${codeBlock}
    @endfor
    /* END COMPILED SCALAR CODE */
    )
      }
    } catch ( Throwable t ) {
      __self.getNotifications ( ).add ( Notification.error ( t .getMessage ( ), t ) )
    return false;
    }

    return true;
}
}

