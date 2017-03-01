package org.fiolino.searcher.result;

import org.fiolino.data.base.Text;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A ResultItem contains a result object. It also stores all Text fields so that Highlight information
 * can be set.
 * <p/>
 * Created by Michael Kuhlmann on 12.01.2016.
 */
public abstract class ResultItem<T> {
  private final T bean;
  private Object id;

  /**
   * The parent is the main entity.
   */
  private static class Parent<T> extends ResultItem<T> {
    private final Map<String, Text[]> textValues = new HashMap<>();

    Parent(T item) {
      super(item);
    }

    @Override
    public void setText(String[] names, Text[] values) {
      if (values != null) {
        assert names.length > 0;
        for (String n : names) {
          textValues.put(n, values);
        }
      }
    }

    @Override
    public Text[] getText(String name) {
      return textValues.get(name);
    }
  }

  /**
   * The child is the destination in a to-one relation.
   */
  private static class Child<T> extends ResultItem<T> {
    private final ResultItem<?> parent;

    Child(ResultItem<?> parent, T item) {
      super(item);
      this.parent = parent;
    }

    @Override
    void setText(String[] names, Text[] values) {
      parent.setText(names, values);
    }

    @Override
    public Text[] getText(String name) {
      return parent.getText(name);
    }
  }

  public static <T> ResultItem<T> create(T item) {
    return new Parent<>(item);
  }

  private ResultItem(T bean) {
    this.bean = bean;
  }

  abstract void setText(String[] names, Text[] values);

  public final void addText(String[] names, Text... values) {
    int vl = values.length;
    if (vl == 0) {
      return;
    }
    if (names.length == 0) {
      throw new IllegalArgumentException("No name given for " + Arrays.toString(values));
    }
    Text[] existing = getText(names[0]);
    if (existing == null) {
      setText(names, values);
    } else {
      int l = existing.length;
      Text[] copy = Arrays.copyOf(existing, l + vl);
      System.arraycopy(values, 0, copy, l, vl);
      setText(names, copy);
    }
  }

  public abstract Text[] getText(String name);

  /**
   * Creates a child item, which means, in a To-One relation the destination.
   */
  public <U> ResultItem<U> createChild(U item) {
    return new Child<>(this, item);
  }

  public T getBean() {
    return bean;
  }

  public Object getId() {
    return id;
  }

  public void setId(Object id) {
    this.id = id;
  }

  @Override
  public boolean equals(Object obj) {
    return obj != null && obj.getClass().equals(getClass()) && ((ResultItem<?>) obj).getBean().equals(getBean());
  }

  @Override
  public int hashCode() {
    return getBean().hashCode() * 31 + 287;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ": " + getBean();
  }
}
