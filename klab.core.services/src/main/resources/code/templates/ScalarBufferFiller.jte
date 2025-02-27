@import org.integratedmodelling.klab.runtime.computation.TemplateCodeInfo
@import gg.jte.support.ForSupport
@param TemplateCodeInfo codeInfo

class ${codeInfo.getClassName()} extends ExpressionBase {
@for(var field : codeInfo.getFieldDeclarations())
  ${field}
@endfor
  ${codeInfo.getClassName()}(ServiceContextScope scope, Observation self @for(var param : ForSupport.of(codeInfo.getConstructorArguments())), ${param.get()}@endfor
  ) {
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

