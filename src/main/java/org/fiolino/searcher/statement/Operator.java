package org.fiolino.searcher.statement;

/**
 * Created by kuli on 10.03.16.
 */
enum Operator {
  AND {
    @Override
    Operator value(boolean negated) {
      return negated ? OR : AND;
    }
  },
  OR{
    @Override
    Operator value(boolean negated) {
      return negated ? AND : OR;
    }
  };

  abstract Operator value(boolean negated);
}
