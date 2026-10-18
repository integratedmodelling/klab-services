package org.integratedmodelling.klab.authority.snapshot;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.services.reasoner.internal.CachedAuthority;

@org.integratedmodelling.klab.api.services.reasoner.Authority(urn="test.snapshot",version="1.0.0",embeddable=true,searchable=true)
public class SnapshotProvider implements SnapshottingAuthority {
  private String id;
  public String getUrn(){return "test.snapshot";}
  public CachePolicy getCachePolicy(){return new CachePolicy("snapshot-test",0,0,0);}
  public Set<String> getSemanticBoundaries(){return Set.of("COUNTRY");}
  public String configure(ConfigurationRequest request){throw new AssertionError("Offline replay must never call live configure");}
  public String restoreConfiguration(ConfigurationRequest request,ConfiguredAuthoritySnapshot snapshot){
    if(!snapshot.request().equals(request)||!snapshot.format().equals("fixture-v1")
        ||!new String(snapshot.state(),StandardCharsets.UTF_8).equals("frozen-vocabulary"))throw new IllegalArgumentException("Changed snapshot");
    return id="restored:"+request.name();
  }
  public ConfiguredAuthoritySnapshot captureConfiguration(String configuration){throw new UnsupportedOperationException("Test fixture is replay-only");}
  public void releaseConfiguration(String configuration){check(configuration);id=null;}
  public void close(){id=null;}
  private void check(String configuration){if(id==null||!id.equals(configuration))throw new IllegalStateException("Released bridge");}
  public Identity resolveIdentity(String configuration,String code){return resolveIdentity(configuration,code,ResolutionMode.CONFIGURED);}
  public Identity resolveIdentity(String configuration,String code,ResolutionMode mode){
    check(configuration);boolean country=code.equals("Spain");
    return new CachedAuthority.StoredIdentity(code,code,"UN.M49",null,
        mode==ResolutionMode.FULL_HIERARCHY?List.of("Europe"):List.of(),List.of(),"frozen",code,1,
        "UN.M49:["+code+"]",Map.of(),country?Set.of("COUNTRY"):Set.of(),
        country&&mode==ResolutionMode.CONFIGURED?HierarchyStatus.DEFERRED:HierarchyStatus.COMPLETE);
  }
  public List<Identity> search(String query,String filter,String configuration){return List.of(resolveIdentity(configuration,query));}
  public Identity reconcile(String configuration,Map<String,String> fields){return resolveIdentity(configuration,fields.get("name"));}
  public Authority subAuthority(String name){return null;}
  public Capabilities getCapabilities(){return new SnapshotCapabilities();}
  public static class SnapshotCapabilities implements Capabilities {
    public String getDescription(){return "Offline configured snapshot fixture";}
    public boolean isSearchable(){return true;}public boolean isFuzzy(){return false;}
    public List<Pair<String,String>> getSubAuthorities(){return List.of();}
    public List<String> getDocumentationFormats(){return List.of();}public String getWorldview(){return null;}
  }
}
