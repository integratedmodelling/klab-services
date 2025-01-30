package org.integratedmodelling.klab.runtime.computation;

import groovy.lang.Binding;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import java.lang.reflect.Constructor;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

public class KlabGroovyShell extends GroovyShell {

    private static final String BASE_ACTION_CLASS = "org.integratedmodelling.klab.extensions.groovy.ExpressionBase";

    private static CompilerConfiguration getConfiguration() {
		CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
		compilerConfiguration.setScriptBaseClass(BASE_ACTION_CLASS);
		ImportCustomizer customizer = new ImportCustomizer();
//		for (Class<?> cls : Extensions.INSTANCE.getKimImports()) {
//			customizer.addImport(Utils.Paths.getLast(cls.getCanonicalName(), '.'), cls.getCanonicalName());
//		}
		compilerConfiguration.addCompilationCustomizers(customizer);
		return compilerConfiguration;
	}

    public KlabGroovyShell() {
        super(KlabGroovyShell.class.getClassLoader(), getConfiguration());
    }

    public Class<ExpressionBase> parseToClass(final String scriptText) throws CompilationFailedException {
        GroovyCodeSource gcs = /*AccessController.doPrivileged(new PrivilegedAction<GroovyCodeSource>(){
            public GroovyCodeSource run() {
                return*/ new GroovyCodeSource(scriptText, generateScriptName(), DEFAULT_CODE_BASE);
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
