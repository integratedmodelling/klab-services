//package org.integratedmodelling.klab.runtime.kactors.extension;
//
//import java.lang.reflect.Method;
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.Map;
//import java.util.Set;
//
//import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
//import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
//import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Call;
//
//public class Library {
//
//    private String name;
//    private Class<?> cls;
//    private Map<String, CallDescriptor> methods = Collections.synchronizedMap(new HashMap<>());
//    private Set<KActorsBehavior.Type> defaulted = new HashSet<>();
//
//    public class CallDescriptor {
//
//        public CallDescriptor(Call cid, Method method) {
//            this.method = method;
//            this.descriptor = cid;
//        }
//
//        public CallDescriptor(KActorsAction action) {
//            this.action = action;
//        }
//
//        private Method method;
//        private Call descriptor;
//        private KActorsAction action;
//        public Method getMethod() {
//            return method;
//        }
//
//        public void setMethod(Method method) {
//            this.method = method;
//        }
//
//        public Call getDescriptor() {
//            return descriptor;
//        }
//
//        public void setDescriptor(Call descriptor) {
//            this.descriptor = descriptor;
//        }
//
//        public KActorsAction getAction() {
//            return action;
//        }
//
//        public void setAction(KActorsAction action) {
//            this.action = action;
//        }
//
//    }
//
//    public Library(String name, Class<?> cls) {
//        this.name = name;
//        this.cls = cls;
//    }
//
//    public String getName() {
//        return name;
//    }
//
//    public void setName(String name) {
//        this.name = name;
//    }
//
//    public Class<?> getCls() {
//        return cls;
//    }
//
//    public void setCls(Class<?> cls) {
//        this.cls = cls;
//    }
//
//    public Map<String, CallDescriptor> getMethods() {
//        return methods;
//    }
//
//    public void setMethods(Map<String, CallDescriptor> methods) {
//        this.methods = methods;
//    }
//
//    public Set<KActorsBehavior.Type> getDefaulted() {
//        return defaulted;
//    }
//
//    public void setDefaulted(Set<KActorsBehavior.Type> defaulted) {
//        this.defaulted = defaulted;
//    }
//
//}
