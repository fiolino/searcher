package org.fiolino.searcher.result;

/**
 * Created by kuli on 23.03.15.
 */
public final class SingleFacetItem<T> implements Comparable<SingleFacetItem<T>> {

    private final T value;
    private final int hitCount;

    SingleFacetItem(T value, int hitCount) {
        this.value = value;
        this.hitCount = hitCount;
    }

    @Override
    public int compareTo(SingleFacetItem<T> other) {
        return other.hitCount - hitCount;
    }

    public T getValue() {
        return value;
    }

    public int getHitCount() {
        return hitCount;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this
                || obj != null && obj.getClass().equals(getClass()) && ((SingleFacetItem<?>)obj).value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode() + 17;
    }

    @Override
    public String toString() {
        return value + " (" + hitCount + ")";
    }
}
