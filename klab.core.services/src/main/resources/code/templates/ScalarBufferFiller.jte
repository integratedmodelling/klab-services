@import org.integratedmodelling.klab.runtime.computation.TemplateCodeInfo
@import gg.jte.support.ForSupport
@param TemplateCodeInfo codeInfo

import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.runtime.computation.ExpressionBase
import org.integratedmodelling.klab.api.scope.ContextScope
import org.integratedmodelling.klab.api.utils.Utils;

class ${codeInfo.getClassName()} extends ExpressionBase {

@for(var field : codeInfo.getFieldDeclarations())
  ${field}
@endfor
  ${codeInfo.getClassName()}(ContextScope scope, Observation self @for(var param : ForSupport.of(codeInfo.getConstructorArguments())), ${param.get()}@endfor) {
    super(scope, self)
    @for(var param : ForSupport.of(codeInfo.getConstructorInitializationStatements()))
    ${param.get()}
    @endfor
  }

  @Override
  Object run() {
@for(var localVar : codeInfo.getLocalVariableDeclarations()) {
    ${localVar}
@endfor
    <%-- Buffers from the fields --%>
    def ret = Utils.Java.distributeComputation(
        bufferSets, // must be a field with a buffer array
        bufferArray -> {
            while bufferArray[0].hasNext()) {
               <%-- LOOP VARIABLES--%>
               def elevation = bufferArray[1].next()
               def slope = bufferArray[2].next()
               <%--self as scalar, too--%>
               def value = <%--THE EXPRS, pass the value around--%>
@for(var codeBlock : codeInfo.getMainCodeBlocks()) {
                  ${codeBlock}
@endfor
               selfBuffer.add(value)
        }
    });
    return ret;
  }
}

