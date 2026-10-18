package org.integratedmodelling.klab.runtime.language;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.languages.api.SemanticSyntax;

/** Exact canonical quality specializations already represented by built-in semantic kinds. */
public final class CoreAliasTypeSupport {
  private record Kind(SemanticSyntax.Type syntax, SemanticType semantic) {}
  private static final Map<String,Kind> SPECIALIZATIONS=Map.of(
      "odo:Ratio",new Kind(SemanticSyntax.Type.RATIO,SemanticType.RATIO),
      "odo:Numerosity",new Kind(SemanticSyntax.Type.NUMEROSITY,SemanticType.NUMEROSITY),
      "odo:Proportion",new Kind(SemanticSyntax.Type.PROPORTION,SemanticType.PROPORTION));
  private CoreAliasTypeSupport() {}
  private static Kind kind(String target) {
    if(target!=null && target.startsWith("http://integratedmodelling.org/odo#"))
      target="odo:"+target.substring("http://integratedmodelling.org/odo#".length());
    return SPECIALIZATIONS.get(target==null?"":target);
  }
  public static SemanticSyntax.Type syntaxType(String target,SemanticSyntax.Type declared) {
    var kind=kind(target);
    if(kind==null)return declared;
    if(declared!=SemanticSyntax.Type.QUANTITY && declared!=SemanticSyntax.Type.GENERIC_QUALITY
        && declared!=kind.syntax())
      throw new IllegalArgumentException("Core alias kind conflicts with canonical target "+target);
    return kind.syntax();
  }
  /** Replace the coarse QUANTITY kind, retaining declaration status and other compatible flags. */
  public static Set<SemanticType> types(String target,Set<SemanticType> original) {
    var result=EnumSet.noneOf(SemanticType.class);
    if(original!=null)result.addAll(original);
    var kind=kind(target);
    if(kind==null)return result;
    if(result.contains(SemanticType.NOTHING))
      throw new IllegalArgumentException("Unresolved core alias kind for canonical target "+target);
    var declarable=EnumSet.copyOf(result);declarable.retainAll(SemanticType.DECLARABLE_TYPES);
    declarable.remove(SemanticType.QUANTITY);declarable.remove(kind.semantic());
    if(!declarable.isEmpty())
      throw new IllegalArgumentException("Core alias kind conflicts with canonical target "+target);
    result.remove(SemanticType.QUANTITY);
    result.addAll(EnumSet.of(SemanticType.OBSERVABLE,SemanticType.QUALITY,SemanticType.QUANTIFIABLE,kind.semantic()));
    return result;
  }
  public static Set<SemanticType> forSyntaxType(SemanticSyntax.Type type,Set<SemanticType> original) {
    for(var entry:SPECIALIZATIONS.entrySet())
      if(entry.getValue().syntax()==type)return types(entry.getKey(),original);
    return types(null,original);
  }
}
