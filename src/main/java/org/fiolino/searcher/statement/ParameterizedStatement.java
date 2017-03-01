package org.fiolino.searcher.statement;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by kuli on 29.12.16.
 */
public class ParameterizedStatement extends ChainedStatement {
  private final String type;
  private final Map<String, Object> parameters = new LinkedHashMap<>();

  public ParameterizedStatement(Statement next, String type) {
    super(next);
    this.type = type;
  }

  @Override
  protected void applyLocalParamsTo(ParamContainer container) {
    super.applyLocalParamsTo(container);
    container.add(type);
    for (Map.Entry<String, Object> e : parameters.entrySet()) {
      container.add(e.getKey()).append('=').append(e.getValue());
    }
  }

  public ParameterizedStatement add(String key, Object value) {
    parameters.put(key, value);
    return this;
  }
}
