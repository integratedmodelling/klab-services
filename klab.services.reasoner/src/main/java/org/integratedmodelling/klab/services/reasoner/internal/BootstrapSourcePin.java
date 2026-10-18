package org.integratedmodelling.klab.services.reasoner.internal;

import java.nio.file.*;
import java.net.URI;
import java.lang.reflect.*;
import java.util.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions.ComponentDescriptor;

/** Explicit process-local approval boundary; never a global local-first discovery preference. */
public record BootstrapSourcePin(String serviceId, String url, Map<String, Artifact> components) {
  public static final String PROPERTY="klab.authority.bootstrap.pin";
  public record Artifact(String componentVersion,String authorityVersion,String md5,String sha256) {
    public Artifact {
      if(componentVersion==null || !componentVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9_.-]+)?")
          || authorityVersion==null || !authorityVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9_.-]+)?")
          || md5==null || !md5.matches("[0-9a-f]{32}") || sha256==null || !sha256.matches("[0-9a-f]{64}"))
        throw new IllegalArgumentException("Bootstrap artifact pin must contain exact versions and content digests");
    }
  }
  public BootstrapSourcePin {
    if(serviceId==null || !serviceId.matches("[A-Za-z0-9_.-]{1,128}")) throw new IllegalArgumentException("Invalid pinned service identity");
    URI uri=URI.create(Objects.requireNonNull(url));
    if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null
        || !Set.of("127.0.0.1","localhost","::1","[::1]").contains(uri.getHost())
        || uri.getPort()<1 || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
      throw new IllegalArgumentException("Bootstrap source pin must name an explicit loopback service URL");
    url=uri.normalize().toString().replaceFirst("/+$", "");
    components=Map.copyOf(components);
    if(components.isEmpty() || components.size()>32 || components.keySet().stream().anyMatch(
        id -> !id.matches("[a-z][a-z0-9_.-]{0,127}")))
      throw new IllegalArgumentException("Explicit bootstrap pins require bounded component identities");
  }
  static BootstrapSourcePin fromSystemProperty() {
    String path=System.getProperty(PROPERTY);if(path==null)return null;
    try {return read(Path.of(path));}
    catch(Exception failure){throw new IllegalArgumentException("Invalid explicit bootstrap pin file");}
  }
  static BootstrapSourcePin read(Path path)throws Exception {
    if(!path.isAbsolute() || !Files.isRegularFile(path) || Files.size(path)>65536)
      throw new IllegalArgumentException("Bootstrap pin requires a small explicit absolute file");
    var pin=new com.fasterxml.jackson.databind.ObjectMapper().readValue(Files.readAllBytes(path),BootstrapSourcePin.class);
    if(pin==null)throw new IllegalArgumentException("Bootstrap pin document must be an object");
    return pin;
  }
  String revision() {
    try {
      var value=List.of(serviceId,url,new TreeMap<>(components));
      var bytes=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(value);
      return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }catch(Exception failure){throw new IllegalStateException("Cannot fingerprint bootstrap pin",failure);}
  }
  void requireBinding(Worldview.AuthorityBinding binding) {
    var pin=components.get(binding.componentUrn());
    if(pin==null || !pin.componentVersion().equals(binding.componentVersion().toString())
        || !pin.authorityVersion().equals(binding.provider().version().toString()))
      throw new IllegalStateException("Worldview authority versions differ from explicit approval");
  }
  void pinResource(ComponentDescriptor component,ResourceSet.Resource resource,ResourceSet resources) {
    var pin=components.get(component.id());
    if(pin==null || !pin.componentVersion().equals(component.version().toString()) || !pin.md5().equals(component.fileHash())
        || !serviceId.equals(resource.getServiceId()) || !resources.getServices().containsKey(serviceId)
        || !url.equals(resources.getServices().get(serviceId).toExternalForm().replaceFirst("/+$", "")))
      throw new IllegalStateException("Selected component content or source differs from explicit approval");
    resource.getMetadata().put("authority.bootstrap.expected.sha256",pin.sha256());
    resource.getMetadata().put("authority.bootstrap.source.url",url);
  }
  Scope scope(Scope owner) {
    var matches=owner.getServices(ResourcesService.class).stream().filter(s->serviceId.equals(s.serviceId())
        && s.getUrl()!=null && url.equals(s.getUrl().toExternalForm().replaceFirst("/+$", ""))).toList();
    if(matches.size()!=1) throw new IllegalStateException("Explicitly pinned Resources service is unavailable or ambiguous");
    var selected=matches.getFirst();
    Class<?> type=owner instanceof ContextScope?ContextScope.class:owner instanceof SessionScope?SessionScope.class:
        owner instanceof UserScope?UserScope.class:owner instanceof ServiceScope?ServiceScope.class:Scope.class;
    return (Scope)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
      if(args!=null && args.length>0 && args[0]==ResourcesService.class) {
        if(method.getName().equals("getServices"))return List.of(selected);
        if(method.getName().equals("getService"))return selected;
        if(method.getName().equals("findService")) {
          @SuppressWarnings("unchecked") var predicate=(java.util.function.Predicate<ResourcesService>)args[1];
          return predicate==null || predicate.test(selected)?Optional.of(selected):Optional.empty();
        }
      }
      // Preserve the original identity, UserScope/session/context contract and all rights.
      try{return method.invoke(owner,args);}catch(InvocationTargetException failure){throw failure.getCause();}
    });
  }
}
