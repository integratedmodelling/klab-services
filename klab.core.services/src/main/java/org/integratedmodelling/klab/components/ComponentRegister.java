package org.integratedmodelling.klab.components;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import javassist.Modifier;
import org.integratedmodelling.common.lang.PrototypeImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.Prototype;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.adapters.ResourceAdapter;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabAnnotation;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.extension.KlabComponent;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class ComponentRegister {


    record AdapterDescriptor(String name) {
    }

    record LibraryDescriptor(String name, String description, List<Prototype> prototypes) {
    }

    record ComponentDescriptor(String id, Version version, List<LibraryDescriptor> libraries,
                               List<AdapterDescriptor> adapters) {
    }

    /**
     * Discover and register all the extensions provided by this component but do not start it.
     *
     * @param component
     */
    public void registerComponent(KlabComponent component) {

        var componentName = component.getName();
        var componentVersion = component.getVersion();
        var libraries = new ArrayList<LibraryDescriptor>();
        var adapters = new ArrayList<AdapterDescriptor>();

        scanPackage(component, Map.of(
                Library.class, (annotation, cls) -> registerLibrary(component, (Library) annotation, cls, libraries),
                ResourceAdapter.class, (annotation, cls) -> registerAdapter(component,
                        (ResourceAdapter) annotation, cls, adapters)));

    }

    private void registerLibrary(KlabComponent component, Library annotation, Class<?> cls, List<LibraryDescriptor> libraries) {

        String namespacePrefix = Library.CORE_LIBRARY.equals(annotation.name()) ? "" :
                                 (annotation.name() + ".");

        var prototypes = new ArrayList<Prototype>();

        for (Class<?> clss : cls.getClasses()) {
            if (clss.isAnnotationPresent(KlabFunction.class)) {
                prototypes.add(createContextualizerPrototype(namespacePrefix,
                        clss.getAnnotation(KlabFunction.class), clss,
                        null));
            } else if (clss.isAnnotationPresent(Verb.class)) {
                prototypes.add(createVerbPrototype(namespacePrefix, clss.getAnnotation(Verb.class), clss, null));
            } else if (clss.isAnnotationPresent(KlabAnnotation.class)) {
                prototypes.add(createAnnotationPrototype(namespacePrefix, clss.getAnnotation(KlabAnnotation.class)
                        , clss,
                        null));
            }
        }

        // annotated methods
        for (Method method : cls.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && method.isAnnotationPresent(KlabFunction.class)) {
                prototypes.add(createContextualizerPrototype(namespacePrefix,
                        method.getAnnotation(KlabFunction.class), cls,
                        method));
            } else if (method.isAnnotationPresent(KlabAnnotation.class)) {
                prototypes.add(createAnnotationPrototype(namespacePrefix, cls.getAnnotation(KlabAnnotation.class),
                        cls,
                        method));
            } else if (method.isAnnotationPresent(Verb.class)) {
                prototypes.add(createVerbPrototype(namespacePrefix, cls.getAnnotation(Verb.class), cls, method));
            }
        }

        libraries.add(new LibraryDescriptor(annotation.name(), annotation.description(), prototypes));
    }

    private void registerAdapter(KlabComponent component, ResourceAdapter annotation, Class<?> cls, List<AdapterDescriptor> adapters) {
        System.out.println("ZIO PORCO ADAPTER " + annotation.name());
    }

    /**
     * Start the passed component so that its services can be used.
     *
     * @param component
     */
    public void loadComponent(KlabComponent component) {
    }

    /**
     * Load any component in the passed resource set that is not already present.
     *
     * @param resourceSet
     * @return
     */
    public boolean loadComponents(ResourceSet resourceSet) {
        return false;
    }

    public void scanPackage(KlabComponent component,
                            Map<Class<? extends Annotation>, BiConsumer<Annotation, Class<?>>> annotationHandlers) {

        try (ScanResult scanResult =
                     new ClassGraph()
                             .enableAnnotationInfo()
                             .addClassLoader(component.getWrapper().getPluginClassLoader())
                             .acceptPackages(component.getClass().getPackageName())
                             .scan()) {
            for (Class<? extends Annotation> ah : annotationHandlers.keySet()) {
                for (ClassInfo routeClassInfo : scanResult.getClassesWithAnnotation(ah)) {
                    try {
                        Class<?> cls = Class.forName(routeClassInfo.getName(), false, component.getWrapper().getPluginClassLoader());
                        Annotation annotation = cls.getAnnotation(ah);
                        if (annotation != null) {
                            annotationHandlers.get(ah).accept(annotation, cls);
                        }
                    } catch (ClassNotFoundException e) {
                        Logging.INSTANCE.error(e);
                    }
                }
            }
        }
    }

    public Prototype createVerbPrototype(String namespacePrefix, Verb annotation, Class<?> clss,
                                         Method method) {
        // TODO
        return null;
    }

    public Prototype createContextualizerPrototype(String namespacePrefix, KlabFunction annotation,
                                                   Class<?> clss,
                                                   Method method) {

        var ret = new PrototypeImpl();

        ret.setName(namespacePrefix + annotation.name());
        ret.setDescription(annotation.description());
        ret.setFilter(annotation.filter());
        ret.setImplementation(clss);
        ret.setExecutorMethod(method == null ? null : method.getName());
        ret.setGeometry(annotation.geometry().isEmpty() ? null : Geometry.create(annotation.geometry()));
        ret.setLabel(annotation.dataflowLabel());
        ret.setReentrant(annotation.reentrant());
        ret.setFunctionType(Prototype.FunctionType.FUNCTION);

        for (Artifact.Type a : annotation.type()) {
            ret.getType().add(a);
        }

        for (KlabFunction.Argument argument : annotation.parameters()) {
            var arg = createArgument(argument);
            ret.getArguments().put(arg.getName(), arg);
        }
        for (KlabFunction.Argument argument : annotation.exports()) {
            var arg = createArgument(argument);
            ret.getImports().add(arg);
        }
        for (KlabFunction.Argument argument : annotation.imports()) {
            var arg = createArgument(argument);
            ret.getExports().add(arg);
        }

        return ret;
    }

    public Prototype createAnnotationPrototype(String namespacePrefix, KlabAnnotation annotation,
                                               Class<?> clss,
                                               Method method) {

        var ret = new PrototypeImpl();

        ret.setName(namespacePrefix + annotation.name());
        ret.setDescription(annotation.description());
        ret.setImplementation(clss);
        ret.setExecutorMethod(method == null ? null : method.getName());
        ret.setFunctionType(Prototype.FunctionType.ANNOTATION);
        for (KlabAsset.KnowledgeClass kcl : annotation.targets()) {
            ret.getTargets().add(kcl);
        }

        for (KlabFunction.Argument argument : annotation.parameters()) {
            var arg = createArgument(argument);
            ret.getArguments().put(arg.getName(), arg);
        }

        return ret;
    }

    private PrototypeImpl.ArgumentImpl createArgument(KlabFunction.Argument argument) {
        var arg = new PrototypeImpl.ArgumentImpl();
        arg.setName(argument.name());
        arg.setDescription(argument.description());
        arg.setOptional(argument.optional());
        arg.setConst(argument.constant());
        arg.setArtifact(argument.artifact());
        for (Artifact.Type a : argument.type()) {
            arg.getType().add(a);
        }
        return arg;
    }


}
