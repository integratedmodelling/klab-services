package org.integratedmodelling.klab.runtime.computation;

import groovy.lang.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Arrays;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.utils.Utils;

public class KlabGroovyShell extends GroovyShell {

  private static final String BASE_ACTION_CLASS =
      "org.integratedmodelling.klab.extensions.groovy.ExpressionBase";

  @SuppressWarnings("unchecked")
  public <T extends GroovyObject> T compile(
      String sourceCode, Class<T> resultClass, Object... constructorArguments) {
    try {
      var groovy = getClassLoader().parseClass(sourceCode);
      return (T)
          groovy
              .getConstructor(Utils.Java.mapArgumentsToInterfaces(constructorArguments))
              .newInstance(constructorArguments);
    } catch (Exception e) {
      throw new KlabInternalErrorException(e);
    }
  }

  private static CompilerConfiguration getConfiguration() {
    CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
    compilerConfiguration.setScriptBaseClass(BASE_ACTION_CLASS);
    ImportCustomizer customizer = new ImportCustomizer();
    //		for (Class<?> cls : Extensions.INSTANCE.getKimImports()) {
    //			customizer.addImport(Utils.Paths.getLast(cls.getCanonicalName(), '.'),
    // cls.getCanonicalName());
    //		}
    compilerConfiguration.addCompilationCustomizers(customizer);
    return compilerConfiguration;
  }

  public KlabGroovyShell() {
    super(KlabGroovyShell.class.getClassLoader(), getConfiguration());
  }

  public Class<ExpressionBase> parseToClass(final String scriptText)
      throws CompilationFailedException {
    GroovyCodeSource
        gcs = /*AccessController.doPrivileged(new PrivilegedAction<GroovyCodeSource>(){
              public GroovyCodeSource run() {
                  return*/
            new GroovyCodeSource(scriptText, generateScriptName(), DEFAULT_CODE_BASE);
    /*}
    });*/
    return getClassLoader().parseClass(gcs);
  }

}
