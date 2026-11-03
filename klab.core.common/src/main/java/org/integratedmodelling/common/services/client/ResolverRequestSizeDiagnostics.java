package org.integratedmodelling.common.services.client;

import java.util.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;

/** Opt-in facts about the actual outgoing JSON; never logs body/header values or changes transport. */
public final class ResolverRequestSizeDiagnostics {
  public static final String FLAG="klab.diagnostics.resolverRequestSizes";
  private ResolverRequestSizeDiagnostics() {}
  public static void report(String endpoint,ResolutionRequest request,Scope scope,String json) {
    try {
      if(!Boolean.getBoolean(FLAG))return;
      var facts=new LinkedHashMap<String,Object>();facts.put("endpoint",endpoint);facts.put("target",request.getObservation()==null?null:request.getObservation().getId());
      if(scope instanceof ContextScope context) {
        facts.put("context",context.getId());var cause=context.getConsequenceCause();
        if(cause!=null){facts.put("sourceCommit",cause.triggeringCommitId());facts.put("sourceActivity",cause.triggeringActivityId());facts.put("decision",cause.decisionId());}
        var cycle=context.getResolutionCycleBinding();if(cycle!=null)facts.put("cycle",cycle.cycleId());
      }
      facts.put("detachedMetadataStrings",metadataStrings(request));
      facts.putAll(inspect(json,128*1024*1024,250000,64));
      System.err.println("RESOLVER_REQUEST_SIZE_DIAGNOSTIC "+org.integratedmodelling.common.utils.Utils.Json.asString(facts));
    }catch(RuntimeException ignored){/* Measurements cannot change the real request/result. */}
  }
  public record StringField(String path,int decodedChars) {}
  /** Detached request metadata only: O(1) String.length even when the wire scan hits its budget. */
  private static List<StringField> metadataStrings(ResolutionRequest request) {
    if(request.getObservation()==null)return List.of();
    var fields=new ArrayList<StringField>();int entries=0;
    for(var entry:request.getObservation().getMetadata().entrySet()) {
      if(++entries>128)break;String path="/observation/metadata/delegate/"+Scan.safe(entry.getKey());
      if(entry.getValue() instanceof String value)fields.add(new StringField(path,value.length()));
      else if(entry.getValue() instanceof List<?> list)for(int i=0;i<Math.min(16,list.size());i++)
        if(list.get(i) instanceof String value)fields.add(new StringField(path+'/'+i,value.length()));
    }
    fields.sort(Comparator.comparingInt(StringField::decodedChars).reversed().thenComparing(StringField::path));
    return List.copyOf(fields.subList(0,Math.min(12,fields.size())));
  }
  /** Extract only library constraint numbers; never emits exception text or request data. */
  public static void reportConstraint(Throwable failure) {
    try {
      if(!Boolean.getBoolean(FLAG))return;
      var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());int count=0;
      for(var cause=failure;cause!=null&&count++<8&&seen.add(cause);cause=cause.getCause()) {
        if(!(cause instanceof com.fasterxml.jackson.core.exc.StreamConstraintsException))continue;
        var text=cause.getMessage();if(text==null)continue;text=text.substring(0,Math.min(1024,text.length()));
        var match=java.util.regex.Pattern.compile("String value length \\((\\d{1,12})\\).*maximum allowed \\((\\d{1,12})",java.util.regex.Pattern.DOTALL).matcher(text);
        if(match.find()) {
          var facts=Map.of("constraint","STRING_LENGTH","detectedAtChars",Long.parseLong(match.group(1)),"maxAllowedChars",Long.parseLong(match.group(2)));
          System.err.println("RESOLVER_STRING_CONSTRAINT_DIAGNOSTIC "+org.integratedmodelling.common.utils.Utils.Json.asString(facts));
        }
      }
    }catch(RuntimeException ignored){/* Original exception handling must remain unchanged. */}
  }
  public static Map<String,Object> inspect(String json,int charBudget,int valueBudget,int depthBudget) {
    var scan=new Scan(json,charBudget,valueBudget,depthBudget);boolean complete=false;String failure=null;
    try {scan.value("",0);scan.white();complete=scan.i==json.length();if(!complete)failure="TRAILING_DATA";}
    catch(Stop stop){failure=stop.reason;}
    var result=new LinkedHashMap<String,Object>();result.put("serializedChars",json.length());result.put("scannedChars",scan.i);
    result.put("stoppedInStringPath",scan.currentStringPath);result.put("complete",complete);result.put("stop",failure);result.put("stringCount",scan.stringCount);
    var fields=new ArrayList<>(scan.largest);fields.sort(Comparator.comparingInt(Field::decodedChars).reversed().thenComparing(Field::path));
    result.put("largestStrings",List.copyOf(fields));return Collections.unmodifiableMap(result);
  }
  public record Field(String path,int decodedChars,int encodedChars) {}
  private static final class Stop extends RuntimeException {final String reason;Stop(String reason){super(null,null,false,false);this.reason=reason;}}
  private static final class Scan {
    final String json;final int chars,values,depth;int i,count,stringCount;String currentStringPath;
    final PriorityQueue<Field> largest=new PriorityQueue<>(Comparator.comparingInt(Field::decodedChars).thenComparing(Field::path));
    Scan(String json,int chars,int values,int depth){this.json=Objects.requireNonNull(json);this.chars=chars;this.values=values;this.depth=depth;}
    char peek(){if(i>=json.length())throw new Stop("UNEXPECTED_END");if(i>=chars)throw new Stop("CHAR_BUDGET");return json.charAt(i);}
    void white(){while(i<json.length()&&Character.isWhitespace(peek()))i++;}
    void expect(char expected){white();if(peek()!=expected)throw new Stop("SHAPE");i++;}
    void value(String path,int level) {
      if(level>depth)throw new Stop("DEPTH_BUDGET");if(++count>values)throw new Stop("VALUE_BUDGET");white();char c=peek();
      if(c=='{') {
        i++;white();if(peek()=='}'){i++;return;}
        for(;;){String key=string(true,null);expect(':');value(path+'/'+safe(key),level+1);white();char next=peek();i++;if(next=='}')return;if(next!=',')throw new Stop("SHAPE");white();}
      } else if(c=='[') {
        i++;white();if(peek()==']'){i++;return;}int index=0;
        for(;;){value(path+'/'+index++,level+1);white();char next=peek();i++;if(next==']')return;if(next!=',')throw new Stop("SHAPE");}
      } else if(c=='"')string(false,path);
      else {int begin=i;while(i<json.length()){c=peek();if(c==','||c==']'||c=='}'||Character.isWhitespace(c))break;i++;}if(begin==i)throw new Stop("SHAPE");}
    }
    String string(boolean key,String path) {
      expect('"');if(!key)currentStringPath=path;int begin=i,decoded=0;StringBuilder name=key?new StringBuilder():null;
      for(;;) {
        char c=peek();i++;if(c=='"')break;
        if(c=='\\') {
          c=peek();i++;
          if(c=='u'){int code=0;for(int n=0;n<4;n++){int digit=Character.digit(peek(),16);i++;if(digit<0)throw new Stop("ESCAPE");code=(code<<4)+digit;}c=(char)code;}
          else c=switch(c){case '"','\\','/'->c;case 'b'->'\b';case 'f'->'\f';case 'n'->'\n';case 'r'->'\r';case 't'->'\t';default->throw new Stop("ESCAPE");};
        } else if(c<32)throw new Stop("CONTROL");
        decoded++;if(key&&decoded<=120)name.append(c);
      }
      if(key)return decoded>120?"[redacted-field]":name.toString();
      currentStringPath=null;stringCount++;var field=new Field(path,decoded,i-begin-1);largest.offer(field);if(largest.size()>12)largest.poll();return null;
    }
    static String safe(String name){return name.matches("[A-Za-z0-9_.:-]{1,120}")?name:"[redacted-field]";}
  }
}
