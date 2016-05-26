package edu.berkeley.util.domain

import groovy.util.logging.Slf4j

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

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
    @Deprecated
    protected static <T> boolean contains(Comparator<T> comparator, Collection<ObjectHolder<T>> collection, ObjectHolder<T> o) {
        // if comparator.compare returns 0 for any element, then the object
        // is in the collection
        return collection.any { !comparator.compare(it.object, o.object) }
    }

    protected static <T> boolean contains(Map<ObjectHolder<T>, Boolean> collectionMap, ObjectHolder<T> o) {
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
                                  Map<ObjectHolder<T>, Boolean> targetMap,
                                  Map<ObjectHolder<T>, Boolean> sourceMap,
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
        List<ObjectHolder<T>> deletedObjects = []
        targetMap.keySet().each { ObjectHolder<T> it ->
            if (!containsClosure(sourceMap, it)) {
                removeClosure(it.object)
                deletedObjects.add(it)
            }
        }
        deletedObjects.each { ObjectHolder<T> key ->
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
        sourceMap.keySet().each { ObjectHolder<T> it ->
            //start = new Date().time
            boolean doesContain = containsClosure(targetMap, it)
            //totalContainsTime += new Date().time - start
            if (!doesContain) {
                //start = new Date().time
                addClosure(it.object)
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
        Map<ObjectHolder<T>, Boolean> targetCollectionMap = convertToMap(target, (target.size() + source.size()) * 2)
        Map<ObjectHolder<T>, Boolean> sourceCollectionMap = convertToMap(source, source.size() * 2)
        _sync(obj, targetCollectionMap, sourceCollectionMap, flushMode,
                (containsClosure ?:
                        // containsClosure(Map<ObjectHolder<T>, Boolean> source, ObjectHolder<T> targetElement)
                        { Map<ObjectHolder<T>, Boolean> _sourceMap, ObjectHolder<T> targetElement ->
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
    @Deprecated
    public static <T> void sync(def domainObj,
                                Comparator<T> comparator,
                                Collection<T> target,
                                Collection<T> source,
                                FlushMode flushMode) {
        sync(domainObj, target, source, flushMode,
                // containsClosure(Map<T, Boolean> source, ObjectHolder<T> targetElement)
                { Map<ObjectHolder<T>, Boolean> _sourceMap, ObjectHolder<T> targetElement ->
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
        Map<ObjectHolder<LogicalEqualsAndHashCodeInterface>, Boolean> targetCollectionMap = convertToLogicalHashMap(target, (target.size() + source.size()) * 2)
        Map<ObjectHolder<LogicalEqualsAndHashCodeInterface>, Boolean> sourceCollectionMap = convertToLogicalHashMap(source, source.size() * 2)

        _sync(domainObj, targetCollectionMap, sourceCollectionMap, flushMode,
                // containsClosure(Map<ObjecHolder<T>, Boolean> source, ObjectHolder<T> targetElement)
                { Map<ObjectHolder<LogicalEqualsAndHashCodeInterface>, Boolean> _sourceMap, ObjectHolder<LogicalEqualsAndHashCodeInterface> targetElement ->
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

    private static <T> void postRemoveCheckpoint(def domainObj, List<ObjectHolder<T>> deletedObjects, FlushMode flushMode) {
        if (flushMode == FlushMode.FLUSH) {
            boolean refreshRequired = false
            deletedObjects.each { ObjectHolder<T> it ->
                if (it.object == null || it.object?.isDirty()) {
                    if (it.object != null) it.object.save()
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
        c1.each { c1HashCode += it.logicalHashCode() }

        long c2HashCode = 0
        c2.each { c2HashCode += it.logicalHashCode() }

        return c1HashCode == c2HashCode
    }

    // Convert a collection into a map using ObjectHolder proxies
    private static <T> Map<ObjectHolder<T>, Boolean> convertToMap(Collection<T> collection, int initialSize) {
        Map<ObjectHolder<T>, Boolean> map = new LinkedHashMap<ObjectHolder<T>, Boolean>(initialSize)
        collection.each {
            map.put(makeProxy(it), Boolean.TRUE)
        }
        return map
    }

    // Convert a collection into a map using ObjectHolder proxies
    private static Map<ObjectHolder<LogicalEqualsAndHashCodeInterface>, Boolean> convertToLogicalHashMap(Collection<LogicalEqualsAndHashCodeInterface> collection, int initialSize) {
        Map<ObjectHolder<LogicalEqualsAndHashCodeInterface>, Boolean> map = new LinkedHashMap<ObjectHolder<LogicalEqualsAndHashCodeInterface>, Boolean>(initialSize)
        collection.each {
            map.put(makeLogicalHashProxy(it), Boolean.TRUE)
        }
        return map
    }

    private static <T> ObjectHolder<T> makeProxy(T obj) {
        return (ObjectHolder<T>) Proxy.newProxyInstance(
                ObjectHolder.getClassLoader(),
                [ObjectHolder] as Class<?>[],
                new ObjectHolderProxyHandler<T>(new DefaultObjectHolder<T>(obj))
        );
    }

    // We use a proxy so we can override the object's hashCode() and
    // equals() to use logicalHashCode() and logicalEquals() instead.  This
    // is to make the Map work, which is totally reliant on equals() and
    // hashCode() for checking key equality.
    private static <T extends LogicalEqualsAndHashCodeInterface> ObjectHolder<T> makeLogicalHashProxy(T obj) {
        return (ObjectHolder<T>) Proxy.newProxyInstance(
                ObjectHolder.getClassLoader(),
                [ObjectHolder] as Class<?>[],
                new ObjectHolderProxyHandler<T>(new LogicalHashCodeObjectHolder(obj))
        );
    }

    // The InvocationHandler for the proxy
    static class ObjectHolderProxyHandler<T> implements InvocationHandler {
        private ObjectHolder<T> objectHolder

        public ObjectHolderProxyHandler(ObjectHolder<T> objectHolder) {
            this.objectHolder = objectHolder
        }

        @Override
        Object invoke(Object o, Method method, Object[] args) throws Throwable {
            return method.invoke(objectHolder, args)
        }
    }

    static interface ObjectHolder<E> {
        E getObject()
    }

    // default object holder which handles
    // non-LogicalEqualsAndHashCodeInterface objects
    static class DefaultObjectHolder<E> implements ObjectHolder<E> {
        final E object

        DefaultObjectHolder(E object) {
            this.object = object
        }

        @Override
        int hashCode() {
            return object?.hashCode() ?: 0
        }

        @Override
        boolean equals(Object o) {
            return (o == null ? object == null : object.equals((o instanceof ObjectHolder ? ((ObjectHolder) o).object : o)))
        }
    }

    // the object holder which uses logicalHashCode() and logicalEquals()
    // for LogicalEqualsAndHashCodeInterface objects when putting them into
    // a map.
    static class LogicalHashCodeObjectHolder<E extends LogicalEqualsAndHashCodeInterface> implements ObjectHolder<LogicalEqualsAndHashCodeInterface> {
        final E object

        LogicalHashCodeObjectHolder(E object) {
            this.object = object
        }

        @Override
        int hashCode() {
            return object?.logicalHashCode() ?: 0
        }

        @Override
        boolean equals(Object o) {
            return (o == null ? object == null : object.logicalEquals((o instanceof ObjectHolder ? ((ObjectHolder) o).object : o)))
        }
    }
}
