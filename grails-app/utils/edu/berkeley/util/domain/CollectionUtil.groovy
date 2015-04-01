package edu.berkeley.util.domain

class CollectionUtil {
    /**
     * Implements Collection.contains() but using a Comparator to do the
     * equality check.
     */
    public static <T> boolean contains(Comparator<T> comparator, Collection<T> collection, T o) {
        for (T comparison in collection) {
            if (comparator.compare(comparison, o) == 0)
                return true
        }
        return false
    }

    /**
     * Duplicate source collection into target collection.  After this call,
     * target will only contain what's in source.
     */
    public static <T> void duplicate(Comparator<T> comparator, Collection<T> target, Collection<T> source) {
        if (target == null || source == null)
            throw new IllegalArgumentException("target or source cannot be null")
        // add anything in source that target doesn't already have
        source.collect().each {
            if (!contains(comparator, target, it)) {
                if (!target.add(it))
                    throw new RuntimeException("add() failed on $target for $it")
            }
        }
        // remove anything from target that's not in source
        target.collect().each {
            if (!contains(comparator, source, it)) {
                if (!target.remove(it))
                    throw new RuntimeException("remove() failed on $target for $it")
            }
        }
    }
}
