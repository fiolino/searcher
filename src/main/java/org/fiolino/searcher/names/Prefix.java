package org.fiolino.searcher.names;

import java.util.Arrays;

/**
 * Prefix is an unmodifiable object which contains the prefix string for some concatenated name.
 *
 * Various prefixes can be attached together, separated by the given separator.
 *
 * Created by kuli on 15.01.16.
 */
public abstract class Prefix {

    final String separator;

    Prefix(String separator) {
        this.separator = separator;
    }

    /**
     * Creates an array of full names, one for each prefix and suffix combination.
     *
     * @param fieldNames Some names
     * @return An array with at least one entry per name
     */
    public abstract String[] createNames(String... fieldNames);

    /**
     * Checks whether this is the root element.
     */
    public abstract boolean isRoot();

    /**
     * Creates a new prefix with the given path elements.
     *
     * @param subNames Some names; may be multiple ones if there are alternatives
     * @return A Prefix which will return isRoot() == false
     */
    public Prefix newSubPrefix(String... subNames) {
        return new NamedPrefix(createNames(subNames), separator);
    }

    private static class NamedPrefix extends Prefix {
        private final String[] heads;

        NamedPrefix(String[] heads, String separator) {
            super(separator);
            if (heads.length == 0) {
                throw new IllegalArgumentException("No head elements given!");
            }
            this.heads = heads;
        }

        @Override
        public String[] createNames(String... fieldNames) {
            int n = heads.length * fieldNames.length;
            String[] result = new String[n];
            int i = 0;
            for (String h : heads) {
                for (String f : fieldNames) {
                    result[i++] = h + separator + f;
                }
            }
            return result;
        }

        @Override
        public boolean isRoot() {
            return false;
        }

        @Override
        public String toString() {
            return Arrays.toString(heads);
        }
    }

    private static class EmptyPrefix extends Prefix {

        EmptyPrefix(String separator) {
            super(separator);
        }

        @Override
        public String[] createNames(String... fieldNames) {
            return fieldNames;
        }

        @Override
        public boolean isRoot() {
            return true;
        }

        @Override
        public String toString() {
            return "Prefix.root(\"" + separator + "\")";
        }
    }

    /**
     * Creates a root Prefix with the given separator string.
     */
    public static Prefix root(String separator) {
        return new EmptyPrefix(separator);
    }

    private static class Holder {
        private static final Prefix DEFAULT = root("-");
    }

    public static Prefix root() {
        return Holder.DEFAULT;
    }
}
