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

public class KlabGroovyShell extends GroovyShell {

  private static final String BASE_ACTION_CLASS =
      "org.integratedmodelling.klab.extensions.groovy.ExpressionBase";

  @SuppressWarnings("unchecked")
  public <T extends GroovyObject> T compile(
      String sourceCode, Class<T> resultClass, Object... constructorArguments) {
    try {
      Class<?> groovy = getClassLoader().parseClass(sourceCode);
      return (T)
          groovy
              .getConstructor(
                  (Class<?>[])
                      Arrays.stream(constructorArguments)
                          .map(o -> o == null ? Object.class : o.getClass())
                          .toArray())
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

  public ExpressionBase createFromClass(Class<?> script, Binding context) throws Exception {
    ExpressionBase runnable = null;
    try {
      Constructor<?> constructor = script.getConstructor(Binding.class);
      runnable = (ExpressionBase) constructor.newInstance(context);
    } catch (NoSuchMethodException e) {
      // Fallback for non-standard "Script" classes.
      runnable = (ExpressionBase) script.getConstructor().newInstance();
      runnable.setBinding(context);
    }
    return runnable;
  }

  public Object runClass(Class<?> script, Binding context) throws Exception {
    ExpressionBase runnable = null;
    try {
      Constructor<?> constructor = script.getConstructor(Binding.class);
      runnable = (ExpressionBase) constructor.newInstance(context);
    } catch (NoSuchMethodException e) {
      // Fallback for non-standard "Script" classes.
      runnable = (ExpressionBase) script.getConstructor().newInstance();
      runnable.setBinding(context);
    }
    return runnable.run();
  }
}
