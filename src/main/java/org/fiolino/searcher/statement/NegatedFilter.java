package org.fiolino.searcher.statement;

/**
 * Created by kuli on 10.03.16.
 */
class NegatedFilter extends ChainedFilter {
  NegatedFilter(Filter next) {
    super(next);
  }

  @Override
  protected void applyTo(StringBuilder sb, boolean negated, boolean allowsNull) {
    next.applyTo(sb, !negated, allowsNull);
  }
}
