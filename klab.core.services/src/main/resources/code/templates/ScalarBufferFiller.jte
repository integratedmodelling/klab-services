@import org.integratedmodelling.klab.runtime.computation.TemplateCodeInfo
@import gg.jte.support.ForSupport
@param TemplateCodeInfo codeInfo

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
  ${codeInfo.getClassName()}(ContextScope scope, Observation self @for(var param : codeInfo.getConstructorArguments()), ${param}@endfor) {
    super(scope, self)
    @for(var param : codeInfo.getConstructorInitializationStatements())
    ${param}
    @endfor
  }

@Override
boolean run(Geometry geometry) {

  <%-- body initialization. Must include all buffer creation + the bufferSets as a transposed list of buffers, \
       ensuring that each thread has the same topology--%>
  @for(var param : codeInfo.getBodyInitializationStatements())
    ${param}
  @endfor
  <%--this is the "locally parallel" version. A Spark template would do very different things --%>
  return Utils.Java.distributeComputation(scope,
    bufferSets,
    { bufferArray ->
        var scannerArray = bufferArray.stream().map({b -> b.scan()}).toArray();
        while (scannerArray[0].hasNext()) { // template ends here
        <%--extract all needed scalar dependencies as non-boxing scalars from buffers --%>
        <%--e.g. double elevation = bufferArray[1].get() --%>
        @for(var localVar : codeInfo.getLocalVariableDeclarations())
            ${localVar}
        @endfor
        <%--set the buffers based on target, chaining as needed--%>
        <%--e.g. double self = elevation/slope --%>
        @for(var codeBlock : codeInfo.getMainCodeBlocks())
            ${codeBlock}
        @endfor
       }
    })
  }
}

