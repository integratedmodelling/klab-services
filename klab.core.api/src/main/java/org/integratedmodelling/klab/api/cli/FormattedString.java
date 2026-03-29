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

  public record Fragment(String text, java.awt.Color color, Style style) {}

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

  public interface Renderer {
    String render(Fragment fragment);
  }

  public static class PlainRenderer implements Renderer {
    @Override
    public String render(Fragment fragment) {
      return fragment.text;
    }
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

  public String render(Renderer renderer) {
    StringBuffer buffer = new StringBuffer();
    fragmentList.forEach(fragment -> buffer.append(renderer.render(fragment)));
    return buffer.toString();
  }

  // TODO add links, lists and headers

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
    return render(new PlainRenderer());
  }
}
