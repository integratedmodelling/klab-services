package org.integratedmodelling.klab.services.runtime.digitaltwin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.runtime.lifecycle.*;
import org.integratedmodelling.klab.services.scopes.*;
import org.junit.jupiter.api.Test;

class ConfigurationExecutionScopeTest {
 @Test void normalActivityFactorySelectsTransactionAndPreservesFrozenCauseContext() {
  org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
  var user=mock(UserIdentity.class);when(user.getId()).thenReturn("tester");when(user.getUsername()).thenReturn("tester");
  var runtime=mock(org.integratedmodelling.klab.api.services.RuntimeService.class);
  var original=new ServiceContextScope(new ServiceSessionScope(new ServiceUserScope(user,runtime)),DigitalTwin.Configuration.builder().id("context").name("Twin").build(),user);original.setId("context");
  var twin=mock(DigitalTwin.class);original.setDigitalTwin(twin);
  var binding=new ResolutionCycleBinding(1,"cycle","context","runtime",true,null);
  var cycle=new ResolutionCycle<>("cycle","context",true,binding,()->{});
  try {
   var cause=new Consequence.Cause(1,"context",1,2,0,null,null,"decision",Consequence.Kind.CONFIGURATION_DETECTION);
   var source=original.withResolutionCycle(cycle,binding).forConsequence(cause);
   var transaction=mock(DigitalTwin.Transaction.class,withSettings().extraInterfaces(CycleScopedTransaction.class));
   when(((CycleScopedTransaction)transaction).executionScope()).thenReturn(new ServiceContextScope(source));
   when(twin.transaction(any(Activity.class),same(source),any(Object[].class))).thenReturn(transaction);
   var executing=source.executing(Activity.of(Activity.Type.DETECTION));
   assertNull(source.getCurrentTransaction());assertSame(transaction,executing.getCurrentTransaction());
   assertSame(cause,executing.getConsequenceCause());assertSame(cycle,executing.getResolutionCycle());assertSame(binding,executing.getResolutionCycleBinding());assertEquals("context",executing.getId());assertSame(twin,executing.getDigitalTwin());
  }finally{cycle.close();}
 }
}
