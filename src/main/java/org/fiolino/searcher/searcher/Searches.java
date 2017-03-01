package org.fiolino.searcher.searcher;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Created by kuli on 08.01.16.
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface Searches {
    Class<?> value() default void.class;
}
