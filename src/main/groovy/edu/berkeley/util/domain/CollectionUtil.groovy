package edu.berkeley.util.domain

import groovy.util.logging.Slf4j

@Slf4j
class CollectionUtil {
    /**
     * Implements Collection.contains() but using a Comparator to do the
     * equality check.
     */
    public static <T> boolean contains(Comparator<T> comparator, Collection<T> collection, T o) {
        // if comparator.compare returns 0 for any element, then the object
        // is in the collection
        return collection.any { !comparator.compare(it, o) }
    }

    /**
     * Implements Collection.contains() but using logicalEquals() to do the
     * equality check.
     */
    public static boolean contains(Collection<LogicalEqualsAndHashCodeInterface> collection, LogicalEqualsAndHashCodeInterface o) {
        // if comparator.compare returns 0 for any element, then the object
        // is in the collection
        return collection.any { it && it.logicalEquals(o) }
    }

    protected static <T> boolean removeElement(Collection<T> target, T element) {
        if (!target.remove(element))
            throw new RuntimeException("remove() failed on $target for $element")
        return true
    }

    protected static <T> boolean addElement(Collection<T> target, T element) {
        if (!target.add(element))
            throw new RuntimeException("add() failed on $target for $element")
        return true
    }

    /**
     * Synchronize target collection to match source collection.  After this
     * call, target will only contain what's in source.
     *
     * Note this will delete any domain object removed from the target
     * collection.  It will also flush the Hibernate session.
     */
    public static <T> void sync(def obj, Collection<T> target, Collection<T> source, Closure<Boolean> containsClosure, Closure<Boolean> addClosure, Closure<Boolean> removeClosure) {
        if (target == null)
            throw new IllegalArgumentException("target cannot be null")
        if (source == null)
            throw new IllegalArgumentException("source cannot be null")
        // Remove anything from target that's not in source.
        // Removals MUST come before additions.
        def deletedObjects = []
        target.collect().each {
            if (!containsClosure(source, it)) {
                removeElement(target, it)
                deletedObjects.add(it)
            }
        }
        postRemoveCheckpoint(obj, deletedObjects)
        // Add anything in source that target doesn't already have.
        source.collect().each {
            if (!containsClosure(target, it)) {
                addElement(target, it)
            }
        }
        postAddCheckpoint(obj)
    }

    /**
     * Synchronize target collection to match source collection.  After this
     * call, target will only contain what's in source.
     *
     * Note this will delete any domain object removed from the target
     * collection.  It will also flush the Hibernate session.
     */
    public static <T> void sync(def domainObj, Comparator<T> comparator, Collection<T> target, Collection<T> source) {
        sync(domainObj, target, source, { Collection<T> _source, T targetElement ->
            contains(comparator, _source, targetElement)
        }, { element ->
            addElement(target, element)
        }, { element ->
            removeElement(target, element)
        })
    }

    /**
     * Synchronize target collection to match source collection.  After this
     * call, target will only contain what's in source.
     *
     * Note this will delete any domain object removed from the target
     * collection.  It will also flush the Hibernate session.
     */
    public static void sync(def domainObj, Collection<LogicalEqualsAndHashCodeInterface> target, Collection<LogicalEqualsAndHashCodeInterface> source) {
        sync(domainObj, target, source, { Collection<LogicalEqualsAndHashCodeInterface> _source, LogicalEqualsAndHashCodeInterface targetElement ->
            contains(_source, (LogicalEqualsAndHashCodeInterface) targetElement)
        }, { element ->
            addElement(target, element)
        }, { element ->
            removeElement(target, element)
        })
    }

    private static void postRemoveCheckpoint(def domainObj, def deletedObjects) {
        domainObj.save(flush: true)
        boolean refreshRequired = false
        deletedObjects.each {
            if (it?.id != null && it.getClass().get(it.id) != null) {
                refreshRequired = true
            } else if (it?.id == null) {
                refreshRequired = true
            }
        }
        if (refreshRequired)
            domainObj.refresh()
    }

    private static void postAddCheckpoint(def domainObj) {
        domainObj.save(flush: true)
    }

    /**
     * Synchronize target collection to match source collection.  After this
     * call, target will only contain what's in source.
     *
     * Note this will delete any domain object removed from the target
     * collection.  It will also flush the Hibernate session.
     */
    public static void sync(def domainObj, Collection<LogicalEqualsAndHashCodeInterface> target, Collection<LogicalEqualsAndHashCodeInterface> source, Closure<Boolean> addClosure, Closure<Boolean> removeClosure) {
        sync(domainObj, target, source, { Collection<LogicalEqualsAndHashCodeInterface> _source, LogicalEqualsAndHashCodeInterface targetElement ->
            contains(_source, (LogicalEqualsAndHashCodeInterface) targetElement)
        }, addClosure, removeClosure)
    }

    public static boolean logicallyEquivalent(Collection<LogicalEqualsAndHashCodeInterface> c1, Collection<LogicalEqualsAndHashCodeInterface> c2) {
        // check that c1 has everything in c2
        for (LogicalEqualsAndHashCodeInterface obj in c2) {
            if (!contains(c1, obj))
                return false
        }
        // check that c1 doesn't have anything that's not in c2
        for (LogicalEqualsAndHashCodeInterface obj in c1) {
            if (!contains(c2, obj))
                return false
        }
        return true
    }
}
