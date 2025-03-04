@import org.integratedmodelling.klab.runtime.computation.TemplateCodeInfo
@import gg.jte.support.ForSupport
@param TemplateCodeInfo codeInfo

import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.runtime.computation.ExpressionBase
import org.integratedmodelling.klab.api.scope.ContextScope
import org.integratedmodelling.klab.api.utils.Utils;
@for(var localImport : codeInfo.getAdditionalImports()) {
    ${localImport}
@endfor

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
@for(var codeBlock : codeInfo.getMainCodeBlocks()) {
    ${codeBlock}
@endfor
  }
}

