package org.integratedmodelling.common.review;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic comment container to be serialized as the standard "comments.json" attachment for a
 * review stage about a reviewable object.
 */
public class Comments implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private List<Comment> comments = new ArrayList<>();
  private String id;

  public static class Comment {

    public enum Status {
      PENDING,
      RESOLVED,
      REJECTED
    }

    private String text;
    private String author;
    private long timestamp;
    private String id;
    private String parentId;
    private Status status;

    public String getText() {
      return text;
    }

    public void setText(String text) {
      this.text = text;
    }

    public String getAuthor() {
      return author;
    }

    public void setAuthor(String author) {
      this.author = author;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public void setTimestamp(long timestamp) {
      this.timestamp = timestamp;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getParentId() {
      return parentId;
    }

    public void setParentId(String parentId) {
      this.parentId = parentId;
    }

    public Status getStatus() {
      return status;
    }

    public void setStatus(Status status) {
      this.status = status;
    }
  }

  public List<Comment> getComments() {
    return comments;
  }

  public void setComments(List<Comment> comments) {
    this.comments = comments;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}
