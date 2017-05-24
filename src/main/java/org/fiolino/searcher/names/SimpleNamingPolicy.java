package org.fiolino.searcher.names;

import org.fiolino.common.ioc.Component;
import org.fiolino.common.processing.FieldDescription;

/**
 * @author Michael Kuhlmann <michael@kuhlmann.org>
 */
@Component(SimpleNamingPolicy.IDENTIFIER)
public class SimpleNamingPolicy implements NamingPolicy {

    public static final String IDENTIFIER = "simple";

    public String[] names(String[] names, Prefix prefix, FieldDescription valueDescription, FieldType fieldType, Filtered filtered, Cardinality cardinality, boolean hidden) {
        return prefix.isRoot() ? names : null;
    }
}
