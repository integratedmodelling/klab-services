package org.integratedmodelling.klab.runtime.libraries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import org.integratedmodelling.klab.api.services.runtime.extension.Actor;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.junit.jupiter.api.Test;

class CoreActorLibraryStringsTest {

  @Test
  void exposesDescriptorFriendlyStaticVerbs() {
    var actor = CoreActorLibrary.Strings.class.getAnnotation(Actor.class);
    assertNotNull(actor);
    assertEquals("strings", actor.name());

    var names = new HashSet<String>();
    for (var method : CoreActorLibrary.Strings.class.getDeclaredMethods()) {
      if (!Modifier.isPublic(method.getModifiers())) {
        continue;
      }
      var verb = method.getAnnotation(Verb.class);
      assertNotNull(verb, method.getName());
      assertEquals(Verb.Type.FUNCTION, verb.executionType(), method.getName());
      assertTrue(Modifier.isStatic(method.getModifiers()), method.getName());
      assertTrue(names.add(verb.name()), "Duplicate verb name " + verb.name());

      var parameters = method.getParameters();
      for (int i = 1; i < parameters.length; i++) {
        var argument = parameters[i].getAnnotation(Verb.Argument.class);
        assertNotNull(argument, method.getName() + " parameter " + i);
        assertFalse(argument.description().isBlank(), method.getName() + " parameter " + i);
      }
    }
  }

  @Test
  void performsCommonNullSafeStringOperations() {
    assertEquals("hello", CoreActorLibrary.Strings.lowercase(null, "HeLLo"));
    assertEquals("HELLO", CoreActorLibrary.Strings.uppercase(null, "hello"));
    assertEquals("I", CoreActorLibrary.Strings.uppercase(null, "i"));
    assertEquals("Hello", CoreActorLibrary.Strings.capitalize(null, "hello"));
    assertEquals("Hello world", CoreActorLibrary.Strings.labelize(null, "hello_world"));
    assertEquals("hello", CoreActorLibrary.Strings.trim(null, "  hello \n"));
    assertEquals("hello world", CoreActorLibrary.Strings.normalize(null, " hello\t \nworld "));
    assertEquals(0, CoreActorLibrary.Strings.length(null, null));
    assertTrue(CoreActorLibrary.Strings.isEmpty(null, null));

    assertTrue(CoreActorLibrary.Strings.contains(null, "hello", "ell"));
    assertTrue(CoreActorLibrary.Strings.startsWith(null, "hello", "he"));
    assertTrue(CoreActorLibrary.Strings.endsWith(null, "hello", "lo"));
    assertTrue(CoreActorLibrary.Strings.equalsIgnoreCase(null, "Hello", "hELLo"));
    assertEquals(2, CoreActorLibrary.Strings.indexOf(null, "hello", "ll"));
    assertEquals(2, CoreActorLibrary.Strings.count(null, "ababa", "ab"));
    assertTrue(CoreActorLibrary.Strings.matches(null, "abc123", "[a-z]+\\d+"));

    assertEquals("one cat", CoreActorLibrary.Strings.replace(null, "one dog", "dog", "cat"));
    assertEquals("ell", CoreActorLibrary.Strings.substring(null, "hello", 1, 4));
    assertEquals(List.of("a", "b", ""), CoreActorLibrary.Strings.split(null, "a,b,", ","));
    assertEquals(
        List.of("one", "two words", "three"),
        CoreActorLibrary.Strings.tokenize(null, "one \"two words\" three"));
    assertEquals("a,2,c", CoreActorLibrary.Strings.join(null, List.of("a", 2, "c"), ","));
    assertEquals("a2c", CoreActorLibrary.Strings.concat(null, "a", 2, "c"));
    assertEquals("ababab", CoreActorLibrary.Strings.repeat(null, "ab", 3));
    assertEquals("abcd...", CoreActorLibrary.Strings.abbreviate(null, "abcdefgh", 7));
  }
}
