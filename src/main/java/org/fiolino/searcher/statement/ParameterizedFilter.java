package org.fiolino.searcher.statement;

/**
 * Created by kuli on 28.04.16.
 */
class ParameterizedFilter extends ChainedFilter {
  private final String localParam;

  ParameterizedFilter(Filter next, String localParam) {
    super(next);
    this.localParam = localParam;
  }

  @Override
  protected void applyLocalParamsTo(ParamContainer container) {
    super.applyLocalParamsTo(container);
    container.add(localParam);
  }
}
