package org.fiolino.searcher.statement;

/**
 * Created by kuli on 28.04.16.
 */
public final class ParamContainer {
  private final StringBuilder sb = new StringBuilder();
  private boolean isEmpty = true;

  ParamContainer() {
  }

  public StringBuilder add(String parameter) {
    if (isEmpty) {
      isEmpty = false;
      sb.append("{!");
    } else {
      sb.append(' ');
    }
    return sb.append(parameter);
  }

  StringBuilder get() {
    if (!isEmpty) {
      sb.append('}');
    }
    return sb;
  }
}
