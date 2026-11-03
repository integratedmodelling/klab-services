package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Read exact newly committed CLASSIFIED audit effects, never a global scan of existing roles. */
public final class RoleAttributionEvents {
  public static final String METADATA_KEY="klab.consequence.roleAttributions";
  private RoleAttributionEvents() {}
  public static RoleConsequenceRuntime.Arrivals read(KnowledgeGraph.Commit commit,
      ServiceContextScope scope,Function<String,Concept> boundRoleResolver) {
    var roles=new ArrayList<RoleConsequenceRuntime.Arrival>();
    var activityIds=commit.getAddedLinks().stream()
        .filter(link->GraphModel.Relationship.CLASSIFIED.name().equals(link.getThird()))
        .map(link->link.getFirst()).filter(commit.getAddedAssets()::contains).distinct().sorted().toList();
    for(var id:activityIds) {
      var activity=scope.getDigitalTwin().getKnowledgeGraph().getAsset(id,scope,Activity.class);
      if(activity==null)return unavailable("Committed classification activity is no longer accessible");
      var direct=activity.getMetadata().get(METADATA_KEY);
      var classified=activity.getMetadata().get(Metadata.IM_ATTRIBUTIONS);
      if(direct==null&&classified==null)return unavailable("Classification has no attribution evidence");
      if(direct!=null) {
        var result=readRows(direct,true,boundRoleResolver,roles);
        if(result!=null)return unavailable(result);
      }
      if(classified!=null) {
        var result=readRows(classified,false,boundRoleResolver,roles);
        if(result!=null)return unavailable(result);
      }
    }
    var unique=new LinkedHashMap<String,RoleConsequenceRuntime.Arrival>();
    for(var role:roles) {
      var key=role.recipientId()+"|"+role.role().getUrn();
      var previous=unique.putIfAbsent(key,role);
      if(previous!=null&&role.characterize()&&!previous.characterize())unique.put(key,role);
    }
    return new RoleConsequenceRuntime.Arrivals(ConsequenceRecipients.Status.COMPLETE,
        List.copyOf(unique.values()),"Committed role attribution evidence captured");
  }
  private static String readRows(Object data,boolean conferred,Function<String,Concept> resolver,
      List<RoleConsequenceRuntime.Arrival> roles) {
    if(!(data instanceof List<?> rows))return "Malformed committed attribution evidence";
    for(var item:rows) {
      if(!(item instanceof Map<?,?> row))return "Malformed committed attribution row";
      var predicate=row.get(conferred?"role":"predicate");
      if(!(predicate instanceof String urn)||urn.isBlank())return "Committed attribution predicate is missing";
      var role=resolver.apply(urn);
      if(role==null)return "Bound role semantics are unavailable";
      if(!role.is(SemanticType.ROLE)) {
        if(conferred)return "Consequence attribution evidence does not identify a role";
        continue;
      }
      var before=row.get("before");var after=row.get("after");
      if(!(before instanceof String)||!(after instanceof String))return "Role attribution baseline is missing";
      if(before.equals(after))continue;
      var member=row.get(conferred?"recipientId":"memberId");
      if(!(member instanceof Number recipient)||recipient.longValue()<=0)return "Role recipient identity is invalid";
      long source=0;String rule=null;
      if(conferred) {
        if(!(row.get("sourceId") instanceof Number id)||id.longValue()<=0
            ||!(row.get("ruleId") instanceof String name)||name.isBlank())return "Conferred role source evidence is missing";
        source=id.longValue();rule=name;
      }
      roles.add(new RoleConsequenceRuntime.Arrival(source,recipient.longValue(),role,rule,conferred));
    }
    return null;
  }
  private static RoleConsequenceRuntime.Arrivals unavailable(String reason) {
    return new RoleConsequenceRuntime.Arrivals(ConsequenceRecipients.Status.DEFERRED,List.of(),reason);
  }
}
