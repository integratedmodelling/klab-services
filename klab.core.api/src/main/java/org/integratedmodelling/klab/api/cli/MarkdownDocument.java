package org.integratedmodelling.klab.api.cli;

/** A command result rendered as Markdown by graphical clients and text by consoles. */
public record MarkdownDocument(String content) {
  @Override public String toString() { return content; }
}
