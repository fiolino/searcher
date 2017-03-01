package org.fiolino.searcher.statement;

/**
 * Created by kuli on 12.01.17.
 */
public final class WriteProtectedStatement extends ChainedStatement {
  public WriteProtectedStatement(Statement next) {
    super(next);
  }
}
