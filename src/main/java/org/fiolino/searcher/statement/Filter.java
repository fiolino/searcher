package org.fiolino.searcher.statement;

/**
 * Created by kuli on 29.02.16.
 */
public abstract class Filter extends Statement {

  protected Filter() {
  }

  public Filter and(Filter other) {
    return new BooleanFilter(this, other, Operator.AND);
  }

  public Filter or(Filter other) {
    return new BooleanFilter(this, other, Operator.OR);
  }

  public Filter negated() {
    return new NegatedFilter(this);
  }

  public Filter allowsNullValues() {
    return new AllowsNullFilter(this);
  }

  public Filter uncached() {
    return new ParameterizedFilter(this, "cache=false");
  }

  public Filter withCost(int cost) {
    return new ParameterizedFilter(this, "cache=false cost=" + cost);
  }

  @Override
  protected final void applyTo(StringBuilder sb) {
    applyTo(sb, false, false);
  }

  protected abstract void applyTo(StringBuilder sb, boolean negated, boolean allowsNull);

}
