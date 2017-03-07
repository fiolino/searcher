package org.fiolino.searcher.names;

import org.fiolino.common.ioc.Component;
import org.fiolino.common.processing.ValueDescription;

/**
 * @author Michael Kuhlmann <michael@kuhlmann.org>
 */
@Component(NamingPolicy.DEFAULT_NAME)
public class DefaultNamingPolicy implements NamingPolicy {

    @Override
    public String[] names(String[] names, Prefix prefix, ValueDescription valueDescription, FieldType fieldType, Filtered filtered, Cardinality cardinality, boolean hidden) {
        String suffix = fieldType.getSuffix(cardinality, filtered.isFiltered(), hidden);
        if (suffix == null) {
            return null;
        }
        String[] full = prefix.createNames(names);
        for (int i = 0; i < full.length; i++) {
            full[i] += '_' + suffix;
        }
        return full;
    }
}
