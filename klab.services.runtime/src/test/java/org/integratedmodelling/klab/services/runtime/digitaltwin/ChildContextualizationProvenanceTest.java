package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.jgrapht.Graph;
import org.junit.jupiter.api.*;

@Timeout(30)
class ChildContextualizationProvenanceTest {
  private final ServiceContextScope scope=mock(ServiceContextScope.class);
  private final DigitalTwinImpl twin=mock(DigitalTwinImpl.class);
  private DigitalTwinImpl.TransactionImpl root() {
    return twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION),scope,RuntimeAsset.PROVENANCE_ASSET);
  }
  private ObservationImpl observation(long id) {
    var observation=new ObservationImpl();observation.setId(id);observation.setUrn("context."+id);return observation;
  }
  private Activity effect(){return Activity.of(Activity.Type.forContextualization(Contextualization.MEASURE));}

  @Test void persistedTargetAbsentFromLocalGraphIsRegisteredWithoutCreationOrNewIdentity() {
    var root=root();var target=observation(42);var effect=effect();
    assertFalse(root.assets().contains(target));
    var child=root.getChild(effect,scope,target);
    assertEquals(42,target.getId());
    var links=child.outgoing(effect);
    assertEquals(1,links.size());var link=links.iterator().next();
    assertEquals(GraphModel.Relationship.MEASURED,link.type());assertSame(target,link.target());
    assertEquals(1,root.assets().stream().filter(Observation.class::isInstance).count());
    assertTrue(root.incoming(target).stream().noneMatch(edge->edge.type()==GraphModel.Relationship.CREATED));
    assertTrue(root.getGraph().getNodes().isEmpty(),"An existing endpoint is not an exported newly added asset");
  }

  @Test void detachedSameIdTargetUsesCanonicalLocalVertex() {
    var root=root();var canonical=observation(42);var detached=observation(42);
    root.link(root.getActivity(),canonical,GraphModel.Relationship.AFFECTS);
    var effect=effect();var child=root.getChild(effect,scope,detached);
    assertSame(canonical,child.outgoing(effect).iterator().next().target());
    assertEquals(1,root.assets().stream().filter(Observation.class::isInstance).count());
    assertTrue(root.assets().stream().noneMatch(asset->asset==detached));
    assertTrue(root.incoming(canonical).stream().noneMatch(edge->edge.type()==GraphModel.Relationship.CREATED));
    assertTrue(root.getGraph().getNodes().isEmpty());
  }

  @SuppressWarnings("unchecked")
  private void edgeFailureAborts(Throwable failure)throws Exception {
    edgeFailureAborts(failure,null);
  }
  @SuppressWarnings("unchecked")
  private void edgeFailureAborts(Throwable failure,RuntimeException cleanup)throws Exception {
    var root=root();var field=DigitalTwinImpl.TransactionImpl.class.getDeclaredField("graph");field.setAccessible(true);
    var graph=spy((Graph<RuntimeAsset,DigitalTwinImpl.TransactionImpl.RelationshipEdge>)field.get(root));field.set(root,graph);
    doAnswer(call->{
      if(call.getArgument(2,DigitalTwinImpl.TransactionImpl.RelationshipEdge.class).relationship==GraphModel.Relationship.MEASURED)throw failure;
      return call.callRealMethod();
    }).when(graph).addEdge(any(RuntimeAsset.class),any(RuntimeAsset.class),any(DigitalTwinImpl.TransactionImpl.RelationshipEdge.class));
    if(cleanup!=null)doThrow(cleanup).when(scope).unregisterTransaction(any());
    var thrown=assertThrows(failure.getClass(),()->root.getChild(effect(),scope,observation(42)));
    assertSame(failure,thrown);assertTrue(root.isFailed());assertEquals(-1,root.commit());
    if(cleanup!=null)assertArrayEquals(new Throwable[]{cleanup},failure.getSuppressed());
  }
  @Test void genuineEffectLinkFailureFailsAtomicRootAndCannotCommit()throws Exception {
    edgeFailureAborts(new IllegalStateException("injected contextualization edge failure"));
  }
  @Test void constructionErrorAlsoFailsRootAndPreservesOriginalError()throws Exception {
    edgeFailureAborts(new AssertionError("injected contextualization edge error"));
  }
  @Test void scopeRegistrationFailureAfterConstructionFailsSharedRoot() {
    var root=root();var failure=new IllegalStateException("injected child registration failure");
    doThrow(failure).when(scope).registerTransaction(any());
    assertSame(failure,assertThrows(IllegalStateException.class,()->root.getChild(effect(),scope,observation(42))));
    assertTrue(root.isFailed());assertEquals(-1,root.commit());
  }
  @Test void cleanupFailureIsSuppressedWithoutReplacingOriginalEffectFailure()throws Exception {
    edgeFailureAborts(new IllegalStateException("injected effect failure"),new IllegalStateException("injected cleanup failure"));
  }
  @Test void invalidContextualizationCannotLeaveCommittablePartialChild() {
    var root=root();var invalid=mock(Activity.Type.class);
    when(invalid.isContextualization()).thenReturn(true);when(invalid.getContextualization()).thenReturn(Contextualization.VOID);
    var activity=Activity.of(Activity.Type.RESOLUTION);activity.setType(invalid);
    assertThrows(IllegalArgumentException.class,()->root.getChild(activity,scope,observation(42)));
    assertTrue(root.isFailed());assertEquals(-1,root.commit());
  }
}
