package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.junit.jupiter.api.Test;

class AuthorityBootstrapTest {
  private final AtomicReference<String> revision=new AtomicReference<>("provider-1");
  private final AtomicReference<String> ownerName=new AtomicReference<>("owner");
  private final AtomicInteger calls=new AtomicInteger();
  private final ServiceScope scope=mock(ServiceScope.class);
  private final CapturedAuthorityCatalog catalog=mock(CapturedAuthorityCatalog.class);
  AuthorityBootstrapTest() {
    var identity=mock(UserIdentity.class);when(identity.getUsername()).thenAnswer(i->ownerName.get());
    when(identity.getId()).thenReturn("owner-id");when(identity.getGroups()).thenReturn(List.of());
    when(scope.getIdentity()).thenReturn(identity);
    when(catalog.supports(any())).thenReturn(true);
  }
  private WorldviewImpl source(String text) {
    var document=new KimOntologyImpl();document.setUrn("w");document.setSourceCode(text);
    var world=new WorldviewImpl();world.setUrn("w");world.getOntologies().add(document);
    world.getAuthorityBindings().add(new org.integratedmodelling.klab.api.knowledge.Worldview.AuthorityBinding(
        "TAXA","w:Identity","w",0,1,"hash",new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.AuthorityDescriptor(
            "klab.authority.taxa",org.integratedmodelling.klab.api.data.Version.create("1.0.0"),true,true,List.of(),List.of()),
        "klab.authority.taxa",org.integratedmodelling.klab.api.data.Version.create("1.0.0-SNAPSHOT"),null));
    return world;
  }
  private AuthorityBootstrap bootstrap(AuthorityBootstrap.Producer producer) {
    return new AuthorityBootstrap((w,s)->new AuthorityBootstrap.Plan(List.of(),new ResourceSet(),revision.get()),producer);
  }
  @Test void unchangedSuccessfulInputDoesNotRepeatProducerOperations() throws Exception {
    var bootstrap=bootstrap((w,s,p,c)->{calls.incrementAndGet();return catalog;});var source=source("first");var key=AuthorityBootstrap.key(source,scope);
    for(int i=0;i<5;i++) assertSame(catalog,bootstrap.prepare(source,scope,key,()->false));
    assertEquals(1,calls.get());
  }
  @Test void failedConfigurationIsNotRepeatedByPollingOrLeakedInDiagnostic() {
    var bootstrap=bootstrap((w,s,p,c)->{calls.incrementAndGet();throw new IllegalStateException("private parameters secret");});
    var source=source("first");var key=AuthorityBootstrap.key(source,scope);
    for(int i=0;i<4;i++) {
      var failure=assertThrows(IllegalStateException.class,()->bootstrap.prepare(source,scope,key,()->false));
      assertFalse(failure.getMessage().contains("secret"));
    }
    assertEquals(1,calls.get());
  }
  @Test void unrelatedSourceChangesReuseSuccessfulProviderCapture() throws Exception {
    var bootstrap=bootstrap((w,s,p,c)->{calls.incrementAndGet();return catalog;});
    for(String text:List.of("first","second")) {var source=source(text);bootstrap.prepare(source,scope,AuthorityBootstrap.key(source,scope),()->false);}
    assertEquals(1,calls.get());
  }
  @Test void newAuthorityIdentityAndChangedConfigurationRequireFreshCapture() throws Exception {
    var bootstrap=bootstrap((w,s,p,c)->{calls.incrementAndGet();return catalog;});
    var world=source("first");
    var root=new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl();
    root.setUrn("Identity");root.setNamespace("w");root.setAuthorityRequired("TAXA");
    root.getAuthorityParameters().put("urn","klab.authority.taxa");
    world.getOntologies().getFirst().getStatements().add(root);
    bootstrap.prepare(world,scope,AuthorityBootstrap.key(world,scope),()->false);
    // A new unrelated document changes the compilation manifest, not the provider inputs.
    var unrelated=new KimOntologyImpl();unrelated.setUrn("unrelated");unrelated.setSourceCode("unrelated");
    world.getOntologies().add(unrelated);
    bootstrap.prepare(world,scope,AuthorityBootstrap.key(world,scope),()->false);
    assertEquals(1,calls.get());
    var identity=new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();
    identity.setAuthority("TAXA");identity.setAuthorityTerm("new-code");root.setDeclaredParent(identity);
    ((KimOntologyImpl)world.getOntologies().getFirst()).setSourceCode("new identity");
    bootstrap.prepare(world,scope,AuthorityBootstrap.key(world,scope),()->false);
    assertEquals(2,calls.get());
    root.getAuthorityParameters().put("datasetKey",123);
    ((KimOntologyImpl)world.getOntologies().getFirst()).setSourceCode("new configuration");
    bootstrap.prepare(world,scope,AuthorityBootstrap.key(world,scope),()->false);
    assertEquals(3,calls.get());
  }
  @Test void failuresStillRetryAfterSourceChangesAndExpiredResponsesAreNotReused() throws Exception {
    var bootstrap=bootstrap((w,s,p,c)->{if(calls.incrementAndGet()==1)throw new IllegalStateException();return catalog;});
    var first=source("first");
    assertThrows(IllegalStateException.class,()->bootstrap.prepare(first,scope,AuthorityBootstrap.key(first,scope),()->false));
    var second=source("second");bootstrap.prepare(second,scope,AuthorityBootstrap.key(second,scope),()->false);
    when(catalog.hasExpiredResponses()).thenReturn(true);
    var third=source("third");bootstrap.prepare(third,scope,AuthorityBootstrap.key(third,scope),()->false);
    assertEquals(3,calls.get());
  }
  @Test void changedDescriptorRevisionInvalidatesSuccessfulReuse() throws Exception {
    var bootstrap=bootstrap((w,s,p,c)->{calls.incrementAndGet();return catalog;});var source=source("first");var key=AuthorityBootstrap.key(source,scope);
    bootstrap.prepare(source,scope,key,()->false);revision.set("changed-artifact-or-rights");
    assertThrows(IllegalStateException.class,()->bootstrap.prepare(source,scope,key,()->false));assertEquals(1,calls.get());
    source.getMetadata().put("authority.catalog.revision","changed-artifact-or-rights");
    bootstrap.prepare(source,scope,AuthorityBootstrap.key(source,scope),()->false);assertEquals(2,calls.get());
  }
  @Test void ownerChangeDuringRealConfigurationCannotPublishOrReuseCapture() {
    var bootstrap=bootstrap((w,s,p,c)->{calls.incrementAndGet();ownerName.set("different-owner");return catalog;});
    var source=source("first");var key=AuthorityBootstrap.key(source,scope);
    assertThrows(IllegalStateException.class,()->bootstrap.prepare(source,scope,key,()->false));assertEquals(1,calls.get());
  }
  @Test void providerChangeDuringConfigurationCannotPublishCapture() {
    var bootstrap=bootstrap((w,s,p,c)->{revision.set("changed-during-produce");return catalog;});
    var source=source("first");assertThrows(IllegalStateException.class,()->bootstrap.prepare(source,scope,AuthorityBootstrap.key(source,scope),()->false));
  }
  @Test void supersededCaptureDoesNotBecomeReusableSuccess() {
    var cancelled=new AtomicBoolean();var bootstrap=bootstrap((w,s,p,c)->{calls.incrementAndGet();cancelled.set(true);return catalog;});
    var source=source("first");var key=AuthorityBootstrap.key(source,scope);
    assertThrows(CancellationException.class,()->bootstrap.prepare(source,scope,key,cancelled::get));
    cancelled.set(false);assertThrows(CancellationException.class,()->bootstrap.prepare(source,scope,key,cancelled::get));
    assertEquals(2,calls.get());
  }
  @Test void explicitInvalidationAllowsOneFreshAttempt() {
    var bootstrap=bootstrap((w,s,p,c)->{calls.incrementAndGet();throw new IllegalStateException("denied");});var source=source("first");var key=AuthorityBootstrap.key(source,scope);
    assertThrows(Exception.class,()->bootstrap.prepare(source,scope,key,()->false));bootstrap.invalidate();
    assertThrows(Exception.class,()->bootstrap.prepare(source,scope,key,()->false));assertEquals(2,calls.get());
  }
  @Test void missingSuccessfulConfiguredProofCannotBeMemoizedAsAcceptedCapture() {
    when(catalog.supports(any())).thenReturn(false);
    var bootstrap=bootstrap((w,s,p,c)->catalog);var source=source("first");
    assertThrows(IllegalStateException.class,()->bootstrap.prepare(source,scope,AuthorityBootstrap.key(source,scope),()->false));
  }
  @Test void invalidationNeverWaitsOnPendingProviderAndDiscardsItsResult() throws Exception {
    var entered=new CountDownLatch(1);var finish=new CountDownLatch(1);
    var bootstrap=bootstrap((w,s,p,c)->{entered.countDown();assertTrue(finish.await(5,TimeUnit.SECONDS));return catalog;});
    var source=source("first");var key=AuthorityBootstrap.key(source,scope);
    try(var executor=Executors.newSingleThreadExecutor()) {
      var pending=executor.submit(()->bootstrap.prepare(source,scope,key,()->false));assertTrue(entered.await(5,TimeUnit.SECONDS));
      assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),bootstrap::invalidate);finish.countDown();
      var failure=assertThrows(ExecutionException.class,()->pending.get(5,TimeUnit.SECONDS));assertInstanceOf(CancellationException.class,failure.getCause());
    }
  }
  @Test void invalidationDuringSuccessCacheInsertionForcesFreshProducer() throws Exception {
    invalidationDuringCacheInsertion(false);
  }
  @Test void invalidationDuringFailureCacheInsertionForcesFreshProducer() throws Exception {
    invalidationDuringCacheInsertion(true);
  }
  private void invalidationDuringCacheInsertion(boolean failFirst) throws Exception {
    var entered=new CountDownLatch(1);var finish=new CountDownLatch(1);var firstPut=new AtomicBoolean(true);
    var bootstrap=bootstrap((w,s,p,c)->{
      int call=calls.incrementAndGet();
      if(failFirst && call==1) throw new IllegalStateException("private failed configuration");
      return catalog;
    });
    // Deterministically pause after the producer's epoch check, before the map insertion.
    var field=AuthorityBootstrap.class.getDeclaredField("outcomes");field.setAccessible(true);
    field.set(bootstrap,new ConcurrentHashMap<String,Object>() {
      @Override public Object put(String key,Object value) {
        if(firstPut.getAndSet(false)) {
          entered.countDown();
          try {assertTrue(finish.await(5,TimeUnit.SECONDS));}
          catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw new AssertionError(interrupted);}
        }
        return super.put(key,value);
      }
    });
    var source=source("first");var key=AuthorityBootstrap.key(source,scope);
    try(var executor=Executors.newSingleThreadExecutor()) {
      var pending=executor.submit(()->bootstrap.prepare(source,scope,key,()->false));
      assertTrue(entered.await(5,TimeUnit.SECONDS));
      assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),bootstrap::invalidate);
      finish.countDown();
      var stale=assertThrows(ExecutionException.class,()->pending.get(5,TimeUnit.SECONDS));
      assertInstanceOf(CancellationException.class,stale.getCause());
      assertSame(catalog,bootstrap.prepare(source,scope,key,()->false));
      assertSame(catalog,bootstrap.prepare(source,scope,key,()->false));
      assertEquals(2,calls.get(),"explicit invalidation must discard both obsolete successes and failures");
    } finally {finish.countDown();}
  }
}
