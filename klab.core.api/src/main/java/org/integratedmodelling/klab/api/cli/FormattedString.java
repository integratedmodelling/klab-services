package org.integratedmodelling.klab.api.cli;

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

  public record Fragment(String text, Color color, Style style) {}

  private final List<Fragment> fragmentList = new ArrayList<>();

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

  public void add(String text, Color color, Style style) {
    fragmentList.add(new Fragment(text, color, style));
  }

  public void add(String text, Color color) {
    fragmentList.add(new Fragment(text, color, null));
  }

  public void add(String text, Style style) {
    fragmentList.add(new Fragment(text, null, style));
  }

  public void add(String text) {
    fragmentList.add(new Fragment(text, null, null));
  }

  public String render(Renderer renderer) {
    StringBuffer buffer = new StringBuffer();
    fragmentList.forEach(fragment -> buffer.append(renderer.render(fragment)));
    return buffer.toString();
  }

  // TODO add links, lists and headers

  public void addLine(String text, Color color, Style style) {
    add(text + "\n", color, style);
  }

  public void addLine(String text, Color color) {
    add(text + "\n", color);
  }

  public void addLine(String text, Style style) {
    add(text + "\n", style);
  }

  public void addLine(String text) {
    add(text + "\n");
  }

  public void addLine() {
    add("\n");
  }

  public String toString() {
    return render(new PlainRenderer());
  }
}
