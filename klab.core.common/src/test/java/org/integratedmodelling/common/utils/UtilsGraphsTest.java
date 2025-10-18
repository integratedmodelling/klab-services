package org.integratedmodelling.common.utils;

import org.jgrapht.Graph;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class UtilsGraphsTest {

  private static <T> Function<T, T> parentFinder(Map<T, T> parentMap) {
    return parentMap::get;
  }

  @Test
  void testTwoNodesWithNonRootCommonAncestor() {
    // Build a simple hierarchy:
    // R
    // ├─ X
    // │  ├─ A
    // │  └─ B
    // │      └─ D
    // └─ Y
    //    └─ C
    Map<String, String> parent = new HashMap<>();
    parent.put("X", "R");
    parent.put("Y", "R");
    parent.put("A", "X");
    parent.put("B", "X");
    parent.put("C", "Y");
    parent.put("D", "B");
    parent.put("R", null);

    List<String> nodes = List.of("A", "D");
    List<List<String>> paths = Utils.Graphs.findPathsToCommonAncestor(nodes, parentFinder(parent));

    assertNotNull(paths);
    assertEquals(2, paths.size());

    // LCA should be X, so paths exclude X
    assertEquals(List.of("A"), paths.get(0));
    assertEquals(List.of("D", "B"), paths.get(1));
  }

  @Test
  void testMultipleNodesLCAIsRoot() {
    Map<String, String> parent = new HashMap<>();
    parent.put("X", "R");
    parent.put("Y", "R");
    parent.put("A", "X");
    parent.put("B", "X");
    parent.put("C", "Y");
    parent.put("D", "B");
    parent.put("R", null);

    List<String> nodes = List.of("A", "C", "D");
    List<List<String>> paths = Utils.Graphs.findPathsToCommonAncestor(nodes, parentFinder(parent));

    assertNotNull(paths);
    assertEquals(3, paths.size());

    // LCA is R; paths exclude R and go child -> parent order
    assertEquals(List.of("A", "X"), paths.get(0));
    assertEquals(List.of("C", "Y"), paths.get(1));
    assertEquals(List.of("D", "B", "X"), paths.get(2));
  }

  @Test
  void testSingleNodeReturnsEmptyPath() {
    Map<String, String> parent = new HashMap<>();
    parent.put("R", null);

    List<String> nodes = List.of("R");
    List<List<String>> paths = Utils.Graphs.findPathsToCommonAncestor(nodes, parentFinder(parent));

    assertNotNull(paths);
    assertEquals(1, paths.size());
    assertTrue(paths.get(0).isEmpty(), "Path for the ancestor itself should be empty");
  }

  @Test
  void testEmptyInputReturnsEmptyList() {
    List<List<String>> paths =
        Utils.Graphs.findPathsToCommonAncestor(Collections.emptyList(), x -> null);
    assertNotNull(paths);
    assertTrue(paths.isEmpty());
  }

  @Test
  void testNullParentFinderThrows() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> Utils.Graphs.findPathsToCommonAncestor(List.of("A"), null));
    assertTrue(ex.getMessage().toLowerCase().contains("parentfinder"));
  }

  // -------- Tests for Utils.Graphs.findCommonAncestry --------

  @Test
  void testFindCommonAncestry_TwoNodesWithNonRootCommonAncestor() {
    // Hierarchy:
    // R
    // ├─ X
    // │  ├─ A
    // │  └─ B
    // │      └─ D
    // └─ Y
    //    └─ C
    Map<String, String> parent = new HashMap<>();
    parent.put("X", "R");
    parent.put("Y", "R");
    parent.put("A", "X");
    parent.put("B", "X");
    parent.put("C", "Y");
    parent.put("D", "B");
    parent.put("R", null);

    List<String> nodes = List.of("A", "D");
    Utils.Graphs.CommonAncestry<String> result =
        Utils.Graphs.findCommonAncestry(nodes, parentFinder(parent));

    assertNotNull(result);
    assertEquals("X", result.getCommonAncestor());
    assertEquals(2, result.getMaxDistance()); // A->X (1), D->B->X (2)

    List<List<String>> paths = result.getPaths();
    assertEquals(2, paths.size());
    assertEquals(List.of("A"), paths.get(0));
    assertEquals(List.of("D", "B"), paths.get(1));
  }

  @Test
  void testFindCommonAncestry_MultipleNodesLCAIsRoot() {
    Map<String, String> parent = new HashMap<>();
    parent.put("X", "R");
    parent.put("Y", "R");
    parent.put("A", "X");
    parent.put("B", "X");
    parent.put("C", "Y");
    parent.put("D", "B");
    parent.put("R", null);

    List<String> nodes = List.of("A", "C", "D");
    Utils.Graphs.CommonAncestry<String> result =
        Utils.Graphs.findCommonAncestry(nodes, parentFinder(parent));

    assertNotNull(result);
    assertEquals("R", result.getCommonAncestor());
    assertEquals(3, result.getMaxDistance()); // longest is D->B->X->R (3)

    List<List<String>> paths = result.getPaths();
    assertEquals(3, paths.size());
    assertEquals(List.of("A", "X"), paths.get(0));
    assertEquals(List.of("C", "Y"), paths.get(1));
    assertEquals(List.of("D", "B", "X"), paths.get(2));
  }

  @Test
  void testFindCommonAncestry_SingleNode() {
    Map<String, String> parent = new HashMap<>();
    parent.put("R", null);

    List<String> nodes = List.of("R");
    Utils.Graphs.CommonAncestry<String> result =
        Utils.Graphs.findCommonAncestry(nodes, parentFinder(parent));

    assertNotNull(result);
    assertEquals("R", result.getCommonAncestor());
    assertEquals(0, result.getMaxDistance());
    assertEquals(1, result.getPaths().size());
    assertTrue(result.getPaths().get(0).isEmpty());
  }

  @Test
  void testFindCommonAncestry_EmptyInput() {
    Utils.Graphs.CommonAncestry<String> result =
        Utils.Graphs.findCommonAncestry(Collections.emptyList(), x -> null);

    assertNotNull(result);
    assertNull(result.getCommonAncestor());
    assertEquals(0, result.getMaxDistance());
    assertTrue(result.getPaths().isEmpty());
  }

  @Test
  void testFindCommonAncestry_NullParentFinderThrows() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> Utils.Graphs.findCommonAncestry(List.of("A"), null));
    assertTrue(ex.getMessage().toLowerCase().contains("parentfinder"));
  }
}
