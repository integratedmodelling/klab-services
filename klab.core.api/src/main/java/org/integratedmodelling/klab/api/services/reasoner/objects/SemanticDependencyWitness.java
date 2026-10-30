package org.integratedmodelling.klab.api.services.reasoner.objects;

import java.util.List;

/** Leased proof for the supplied semantic signature. COMPLETE never certifies caller plan coverage. */
public record SemanticDependencyWitness(String contractVersion, Status status, String serviceId,
    long knowledgeRevision, String baseFingerprint, String inputHash,
    List<AuthorityUse> authorities, String reason) implements java.io.Serializable {
  public static final String VERSION="semantic-dependency-v1";
  public enum Status { COMPLETE, REVALIDATION_REQUIRED, UNKNOWN, STALE }
  public enum Kind { SNAPSHOT, CAPTURED, UNVERSIONED }
  public record AuthorityUse(String authority, Kind kind, String proof, long expiresAt)
      implements java.io.Serializable {}
  public SemanticDependencyWitness {
    authorities=authorities==null?List.of():List.copyOf(authorities);
  }
  public boolean completeAt(long now) { return wellFormed(this,true,now); }
  public static boolean revalidates(SemanticDependencyWitness previous,SemanticDependencyWitness current) {
    return revalidates(previous,current,System.currentTimeMillis());
  }
  /** Numeric generations remain active-plan fences; durable reuse compares freshly recomputed content. */
  public static boolean revalidates(SemanticDependencyWitness previous,SemanticDependencyWitness current,long now) {
    if(!wellFormed(previous,false,now)||!wellFormed(current,true,now))return false;
    if(!java.util.Objects.equals(previous.serviceId,current.serviceId)
        ||!java.util.Objects.equals(previous.baseFingerprint,current.baseFingerprint)
        ||!java.util.Objects.equals(previous.inputHash,current.inputHash))return false;
    return authorityIdentity(previous).equals(authorityIdentity(current));
  }
  private static java.util.Map<String,String> authorityIdentity(SemanticDependencyWitness value) {
    var result=new java.util.TreeMap<String,String>();
    for(var use:value.authorities)result.put(use.authority,use.kind+":"+use.proof);
    return result;
  }
  private static boolean wellFormed(SemanticDependencyWitness value,boolean fresh,long now) {
    if(value==null||!VERSION.equals(value.contractVersion)||value.status!=Status.COMPLETE
        ||value.serviceId==null||value.serviceId.isBlank()||value.knowledgeRevision<0
        ||!org.integratedmodelling.klab.api.knowledge.SemanticContentFingerprint.scoped(value.baseFingerprint)
        ||value.inputHash==null||!value.inputHash.matches("[a-f0-9]{64}"))return false;
    String previous=null;
    for(var use:value.authorities) {
      if(use==null||use.authority==null||use.authority.isBlank()||use.kind==null
          ||use.kind==Kind.UNVERSIONED||use.proof==null||!use.proof.matches("[a-f0-9]{64}")
          ||previous!=null&&previous.compareTo(use.authority)>=0
          ||use.kind==Kind.SNAPSHOT&&use.expiresAt!=Long.MAX_VALUE
          ||use.kind==Kind.CAPTURED&&use.expiresAt<=0
          ||fresh&&use.expiresAt<=now)return false;
      previous=use.authority;
    }
    return true;
  }
  public static SemanticDependencyWitness unknown(SemanticDependencyRequest request,String reason) {
    return new SemanticDependencyWitness(VERSION,Status.UNKNOWN,null,-1,null,
        request==null?null:request.inputHash(),List.of(),reason);
  }
}
