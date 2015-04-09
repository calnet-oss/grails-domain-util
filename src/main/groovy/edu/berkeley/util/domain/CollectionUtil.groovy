package edu.berkeley.util.domain

class CollectionUtil {
    /**
     * Implements Collection.contains() but using a Comparator to do the
     * equality check.
     */
    public static <T> boolean contains(Comparator<T> comparator, Collection<T> collection, T o) {
        // if comparator.compare returns 0 for any element, then the
        // object is in the collection
        return collection.any { !comparator.compare(it, o) }
    }

    /**
     * Implements Collection.contains() but using logicalEquals() to do the
     * equality check.
     */
    public static boolean contains(Collection<LogicalEqualsAndHashCodeInterface> collection, LogicalEqualsAndHashCodeInterface o) {
        // if comparator.compare returns 0 for any element, then the
        // object is in the collection
        return collection.any { it.logicalEquals(o) }
    }

    /**
     * Synchronize target collection to match source collection.  After this
     * call, target will only contain what's in source.
     */
    public static <T> void sync(Comparator<T> comparator, Collection<T> target, Collection<T> source) {
        if (!target || !source)
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

    /**
     * Synchronize target collection to match source collection.  After this
     * call, target will only contain what's in source.
     */
    public static void sync(Collection<LogicalEqualsAndHashCodeInterface> target, Collection<LogicalEqualsAndHashCodeInterface> source) {
        if (target == null || source == null)
            throw new IllegalArgumentException("target or source cannot be null")
        // add anything in source that target doesn't already have
        source.collect().each {
            if (!contains(target, (LogicalEqualsAndHashCodeInterface)it)) {
                if (!target.add(it))
                    throw new RuntimeException("add() failed on $target for $it")
            }
        }
        // remove anything from target that's not in source
        target.collect().each {
            if (!contains(source, (LogicalEqualsAndHashCodeInterface)it)) {
                if (!target.remove(it))
                    throw new RuntimeException("remove() failed on $target for $it")
            }
        }
    }
}
