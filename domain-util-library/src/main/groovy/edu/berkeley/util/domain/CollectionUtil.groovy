package edu.berkeley.util.domain

import edu.berkeley.calnet.groovy.transform.LogicalEqualsAndHashCodeInterface
import groovy.util.logging.Slf4j

@Slf4j
class CollectionUtil {
    static enum FlushMode {
        NO_FLUSH, FLUSH
    }

    /**
     * Implements Collection.contains() but using a Comparator to do the
     * equality check.
     *
     * @deprecated due to poor performance using Comparators.  Slated for removal.
     */
    /*
    @Deprecated
    protected static <T> boolean contains(Comparator<T> comparator, Collection<T> collection, T o) {
        // if comparator.compare returns 0 for any element, then the object
        // is in the collection
        return collection.any { !comparator.compare(it, o) }
    }
    */

    protected static <T> boolean contains(Map<T, Boolean> collectionMap, T o) {
        return collectionMap.containsKey(o)
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

    private static <T> void _sync(def obj,
                                  Map<T, Boolean> targetMap,
                                  Map<T, Boolean> sourceMap,
                                  FlushMode flushMode,
                                  Closure<Boolean> containsClosure,
                                  Closure<Boolean> addClosure,
                                  Closure<Boolean> removeClosure) {
        if (targetMap == null)
            throw new IllegalArgumentException("target cannot be null")
        if (sourceMap == null)
            throw new IllegalArgumentException("source cannot be null")

        // Remove anything from target that's not in source.
        // Removals MUST come before additions.
        //log.debug("PROFILE: delete: START")
        List<T> deletedObjects = []
        targetMap.keySet().each { T it ->
            if (!containsClosure(sourceMap, it)) {
                removeClosure(it)
                deletedObjects.add(it)
            }
        }
        deletedObjects.each { T key ->
            targetMap.remove(key)
        }
        //log.debug("PROFILE: delete: END")
        //log.debug("PROFILE: postRemove checkpoint: START")
        postRemoveCheckpoint(obj, deletedObjects, flushMode)
        //log.debug("PROFILE: postRemove checkpoint: END")
        // Add anything in source that target doesn't already have.
        //log.debug("PROFILE: add: START")
        //long totalContainsTime = 0
        //long totalAddTime = 0
        //long start = 0
        sourceMap.keySet().each { T it ->
            //start = new Date().time
            boolean doesContain = containsClosure(targetMap, it)
            //totalContainsTime += new Date().time - start
            if (!doesContain) {
                //start = new Date().time
                addClosure(it)
                targetMap.put(it, Boolean.TRUE)
                //totalAddTime = new Date().time - start
            }
        }
        //log.debug("totalContainsTime = ${totalContainsTime}ms, totalAddTime = ${totalAddTime}ms")
        //log.debug("PROFILE: add: END")
        //log.debug("PROFILE: postAdd checkpoint: START")
        postAddCheckpoint(obj, flushMode)
        //log.debug("PROFILE: postAdd checkpoint: END")
    }

    /**
     * Synchronize target collection to match source collection.  After this
     * call, target will only contain what's in source.
     *
     * Note this will delete any domain object removed from the target
     * collection.  It will also flush the Hibernate session.
     *
     * <p/>
     *
     * Since we're dealing with generic objects of unknown type, the objects
     * must implement the same semantics used in a regular Map to determine
     * equality.  If you look at the Map interface JavaDoc, the
     * specification states that equality in a map is determined by:
     * (key==null ?  k==null : key.equals(k)).
     */
    public static <T> void sync(def obj,
                                Collection<T> target,
                                Collection<T> source,
                                FlushMode flushMode,
                                Closure<Boolean> containsClosure,
                                Closure<Boolean> addClosure,
                                Closure<Boolean> removeClosure) {
        // We use temporary maps since we need an efficient way to determine
        // if something from one collection is already in the other.  Since
        // we're dealing with generic objects of unknown type, the objects
        // must implement the same semantics used in a regular Map to
        // determine equality.  If you look at the Map interface JavaDoc,
        // the specification states that equality in a map is determined by:
        // (key==null ?  k==null : key.equals(k)).
        Map<T, Boolean> targetCollectionMap = convertToMap(target, (target.size() + source.size()) * 2)
        Map<T, Boolean> sourceCollectionMap = convertToMap(source, source.size() * 2)
        _sync(obj, targetCollectionMap, sourceCollectionMap, flushMode,
                (containsClosure ?:
                        // containsClosure(Map<T, Boolean> source, T targetElement)
                        { Map<T, Boolean> _sourceMap, T targetElement ->
                            contains(_sourceMap, targetElement)
                        }
                ),
                (addClosure ?:
                        // addClosure(element)
                        { T element ->
                            addElement(target, element)
                        }
                ),
                (removeClosure ?:
                        // removeClosure(element)
                        { T element ->
                            removeElement(target, element)
                        }
                ))
    }

    /**
     * Synchronize target collection to match source collection.  After this
     * call, target will only contain what's in source.
     *
     * Note this will delete any domain object removed from the target
     * collection.  It will also flush the Hibernate session.
     *
     * @deprecated due to poor performance using Comparators.  Slated for removal.
     */
    /*
    @Deprecated
    public static <T> void sync(def domainObj,
                                Comparator<T> comparator,
                                Collection<T> target,
                                Collection<T> source,
                                FlushMode flushMode) {
        sync(domainObj, target, source, flushMode,
                // containsClosure(Map<T, Boolean> source, T targetElement)
                { Map<T, Boolean> _sourceMap, T targetElement ->
                    contains(comparator, _sourceMap.keySet(), targetElement)
                },
                // addClosure(element)
                { T element ->
                    addElement(target, element)
                },
                // removeClosure(element)
                { T element ->
                    removeElement(target, element)
                })
    }
    */

    /**
     * Synchronize target collection to match source collection.  After this
     * call, target will only contain what's in source.
     *
     * Note this will delete any domain object removed from the target
     * collection.  It will also flush the Hibernate session.
     */
    public static void sync(def domainObj,
                            Collection<LogicalEqualsAndHashCodeInterface> target,
                            Collection<LogicalEqualsAndHashCodeInterface> source,
                            FlushMode flushMode,
                            Closure<Boolean> addClosure = null,
                            Closure<Boolean> removeClosure = null) {
        // We use temporary maps since we need an efficient way to determine
        // if something from one collection is already in the other.
        Map<LogicalEqualsAndHashCodeInterface, Boolean> targetCollectionMap = convertToLogicalHashMap(target, (target.size() + source.size()) * 2)
        Map<LogicalEqualsAndHashCodeInterface, Boolean> sourceCollectionMap = convertToLogicalHashMap(source, source.size() * 2)

        _sync(domainObj, targetCollectionMap, sourceCollectionMap, flushMode,
                // containsClosure(Map<T, Boolean> source, T targetElement)
                { Map<LogicalEqualsAndHashCodeInterface, Boolean> _sourceMap, LogicalEqualsAndHashCodeInterface targetElement ->
                    contains(_sourceMap, targetElement)
                },
                (addClosure ?:
                        // addClosure(element)
                        { element ->
                            addElement(target, element)
                        }
                ),
                (removeClosure ?:
                        // removeClosure(element)
                        { element ->
                            removeElement(target, element)
                        }
                ))
    }

    private static <T> void postRemoveCheckpoint(def domainObj, List<T> deletedObjects, FlushMode flushMode) {
        if (flushMode == FlushMode.FLUSH) {
            boolean refreshRequired = false
            deletedObjects.each { T it ->
                if (it == null || it.isDirty()) {
                    if (it != null) it.save()
                    refreshRequired = true
                }
            }
            domainObj.save(flush: true)
            if (refreshRequired) {
                // May get the following error: this instance does not yet
                // exist as a row in the database.  I'm not sure if there's
                // a better way to check for this.
                try {
                    domainObj.refresh()
                }
                catch (Exception e) {
                    log.debug("refresh() exception, probably because $domainObj doesn't exist in database yet, which is ok: ${e.message}")
                }
            }
        }
    }

    private static void postAddCheckpoint(def domainObj, FlushMode flushMode) {
        if (flushMode == FlushMode.FLUSH) {
            domainObj.save(flush: true)
        }
    }

    public static boolean logicallyEquivalent(Collection<LogicalEqualsAndHashCodeInterface> c1, Collection<LogicalEqualsAndHashCodeInterface> c2) {
        if (c1.size() != c2.size())
            return false;

        long c1HashCode = 0
        c1.each { c1HashCode += it.hashCode() }

        long c2HashCode = 0
        c2.each { c2HashCode += it.hashCode() }

        return c1HashCode == c2HashCode
    }

    // Convert a collection into a map
    private static <T> Map<T, Boolean> convertToMap(Collection<T> collection, int initialSize) {
        Map<T, Boolean> map = new LinkedHashMap<T, Boolean>(initialSize)
        collection.each {
            map.put(it, Boolean.TRUE)
        }
        return map
    }

    // Convert a collection into a map
    private static Map<LogicalEqualsAndHashCodeInterface, Boolean> convertToLogicalHashMap(Collection<LogicalEqualsAndHashCodeInterface> collection, int initialSize) {
        Map<LogicalEqualsAndHashCodeInterface, Boolean> map = new LinkedHashMap<LogicalEqualsAndHashCodeInterface, Boolean>(initialSize)
        collection.each {
            map.put(it, Boolean.TRUE)
        }
        return map
    }
}
