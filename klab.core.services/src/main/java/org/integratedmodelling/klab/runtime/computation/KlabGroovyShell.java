package org.integratedmodelling.klab.runtime.computation;

import groovy.lang.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

public class KlabGroovyShell extends GroovyShell {

  private static final String BASE_ACTION_CLASS =
      "org.integratedmodelling.klab.extensions.groovy.ExpressionBase";

  /** Copied snippet for reference */
  public class GroovyRun {
    public static void main(final String[] args)
        throws IllegalAccessException, InstantiationException, IOException {
      // Create GroovyClassLoader.
      try (GroovyClassLoader classLoader = new GroovyClassLoader()) {

        // Create a String with Groovy code.
        final StringBuilder groovyScript = new StringBuilder();
        groovyScript.append("class Sample {");
        groovyScript.append(" String sayIt(name) { \"Groovy says: Cool $name!\" }");
        groovyScript.append("}");

        // Load string as Groovy script class.
        Class groovy = classLoader.parseClass(groovyScript.toString());
        GroovyObject groovyObj = (GroovyObject) groovy.newInstance();
        // String output = groovyObj.invokeMethod("sayIt", new Object[] { "mrhaki" });
        // assert "Groovy says: Cool mrhaki!".equals(output);

        // Load Groovy script file.
        groovy = classLoader.parseClass(new File("SampleScript.groovy"));
        groovyObj = (GroovyObject) groovy.newInstance();
        var output = groovyObj.invokeMethod("scriptSays", new Object[] {"mrhaki", 2});
        assert "Hello mrhaki, from Groovy. Hello mrhaki, from Groovy. ".equals(output);
      }
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
