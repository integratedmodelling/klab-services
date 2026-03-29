package org.integratedmodelling.klab.api.cli;

import com.jayway.jsonpath.internal.filter.ValueNodes;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A builder for a formatted string whose #renderFragment function can be overridden to support
 * differen rendering engines.
 *
 * <p>Returning this object from a command handler enables rendering the formatted string in
 * different contexts, such as console output, HTML, or other UIs.
 */
public class FormattedString {

  protected record Fragment(String text, java.awt.Color color, Style style) {}

  List<Fragment> fragmentList = new ArrayList<>();

  public enum Color {
    DEFAULT,
    RED,
    GREEN,
    YELLOW,
    BLUE,
    MAGENTA,
    CYAN,
    WHITE,
    BLACK;

    java.awt.Color getColor() {
      return switch (this) {
        case DEFAULT -> null;
        case RED -> java.awt.Color.RED;
        case GREEN -> java.awt.Color.GREEN;
        case YELLOW -> java.awt.Color.YELLOW;
        case BLUE -> java.awt.Color.BLUE;
        case MAGENTA -> java.awt.Color.MAGENTA;
        case CYAN -> java.awt.Color.CYAN;
        case WHITE -> java.awt.Color.WHITE;
        case BLACK -> java.awt.Color.BLACK;
      };
    }
  }

  public enum Style {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH
  }

  void add(String text, Color color, Style style) {
    fragmentList.add(new Fragment(text, color.getColor(), style));
  }

  void add(String text, Color color) {
    fragmentList.add(new Fragment(text, color.getColor(), null));
  }

  void add(String text, java.awt.Color color, Style style) {
    fragmentList.add(new Fragment(text, color, style));
  }

  void add(String text, java.awt.Color color) {
    fragmentList.add(new Fragment(text, color, null));
  }

  void add(String text, Style style) {
    fragmentList.add(new Fragment(text, null, style));
  }

  void add(String text) {
    fragmentList.add(new Fragment(text, null, null));
  }

  public String render() {
    StringBuffer buffer = new StringBuffer();
    fragmentList.forEach(fragment -> buffer.append(renderFragment(fragment)));
    return buffer.toString();
  }

  /**
   * Render a fragment. Override this function to support different rendering engines.
   *
   * @param fragment the fragment to render. Both style and color can be null to mean the engine's
   *     default.
   * @return
   */
  protected String renderFragment(Fragment fragment) {
    return fragment.text;
  }

  void addLine(String text, Color color, Style style) {
    add(text + "\n", color, style);
  }

  void addLine(String text, Color color) {
    add(text + "\n", color);
  }

  void addLine(String text, java.awt.Color color, Style style) {
    add(text + "\n", color, style);
  }

  void addLine(String text, java.awt.Color color) {
    add(text + "\n", color);
  }

  void addLine(String text, Style style) {
    add(text + "\n", style);
  }

  void addLine(String text) {
    add(text + "\n");
  }

  void addLine() {
    add("\n");
  }

  public String toString() {
    StringBuffer buffer = new StringBuffer();
    fragmentList.forEach(fragment -> buffer.append(fragment.text));
    return buffer.toString();
  }
}
