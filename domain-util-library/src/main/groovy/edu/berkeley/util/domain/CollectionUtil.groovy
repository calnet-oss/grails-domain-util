/*
 * Copyright (c) 2016, Regents of the University of California and
 * contributors.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.berkeley.util.domain

import groovy.util.logging.Slf4j

@Slf4j
class CollectionUtil {
    static enum FlushMode {
        NO_FLUSH, FLUSH_DELETES, FLUSH_DELETES_AND_ADDS
    }

    protected static <T> boolean contains(Collection<T> collection, T o) {
        return collection.contains(o)
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
                                  Collection<T> target,
                                  Collection<T> source,
                                  FlushMode flushMode,
                                  Closure<Boolean> addClosure,
                                  Closure<Boolean> removeClosure) {
        if (target == null)
            throw new IllegalArgumentException("target cannot be null")
        if (source == null)
            throw new IllegalArgumentException("source cannot be null")

        /**
         * There is one thing we could do here at a later date to maybe
         * increase efficiency.  That is, if there are rows getting deleted,
         * to re-use those identifiers for the rows getting added.
         */

        //log.warn("SOURCE: " + source)
        //log.warn("TARGET: " + target)

        // Make a map of source identifiers
        Map<Object, T> sourceIdentifiersMap = new HashMap<Object, T>((int) (source.size() * 1.25))
        source.each { if (it.ident() != null) sourceIdentifiersMap.put(it.ident(), it) }
        //log.warn("SOURCE IDS: " + sourceIdentifiersMap)

        // Remove anything from target that's not in source.
        Collection<T> deletedObjects = target.findAll { T it ->
            // If identifiers match but the hash code is different, we don't
            // want to count that as a deleted object.  Instead, it will get
            // replaced.
            if (!contains(source, it)) {
                boolean isNewOrIsNotInSource = (it.ident() == null || !sourceIdentifiersMap.containsKey(it.ident()))
                //log.warn("SOURCE DOES NOT CONTAIN $it, isNewOrIsNotInSource=$isNewOrIsNotInSource")
                return isNewOrIsNotInSource
            } else {
                //log.warn("SOURCE CONTAINS $it")
                return false
            }
        }

        //log.warn("REMOVING $deletedObjects")

        deletedObjects.each {
            removeClosure(it)
            assert !target.contains(it)
        }

        // delete() must come before save() in case we are replacing an
        // object with another that may trigger unique constraint violations
        // if the .delete() and save() order isn't maintained.
        if (flushMode == FlushMode.FLUSH_DELETES || flushMode == FlushMode.FLUSH_DELETES_AND_ADDS) {
            boolean didDelete = false
            deletedObjects.each { delObj ->
                if (!contains(target, delObj)) {
                    T locked = delObj.lock(delObj.ident())
                    if (locked != null) {
                        locked.delete(flush: true)
                        didDelete = true
                    } else {
                        //log.warn("${delObj} has disappeared already.  Can't hard-delete it.")
                    }
                }
            }
            if (didDelete && obj) {
                // Once the individual objects have been deleted, flush the
                // Hibernate session.
                obj.withSession { session ->
                    session.flush()
                }
            }
        }

        // Make a map of target identifiers
        Map<Object, T> targetIdentifiersMap = new HashMap<Object, T>((int) (target.size() * 1.25))
        target.each { if (it.ident() != null) targetIdentifiersMap.put(it.ident(), it) }
        //log.warn("TARGET IDS: " + targetIdentifiersMap)

        // Add anything from the source that's not in target.
        boolean didAdd = false
        source.each { T objToAdd ->
            if (!contains(target, objToAdd)) {
                T targetObject = targetIdentifiersMap.get(objToAdd.ident())
                if (targetObject != null) {
                    // Replace objects with same id but different hash codes
                    //log.warn("REPLACING $objToAdd")
                    // removeClosure won't work here because that removes based on hashCode.
                    // Instead, we want to remove by identifier.
                    Collection<T> objsToRemove = target.findAll { it.ident() == targetObject.ident() }
                    assert objsToRemove
                    objsToRemove.each { objToRemove ->
                        // I don't know why, but with our
                        // DomainCollectionSyncSpec,
                        // target.remove(objToRemove) is returning false. 
                        // I've confirmed target DOES contain objToRemove,
                        // both from a hashCode() and
                        // System.identityHashCode() perspective, as well as
                        // equals() perspective, so I don't know what the
                        // remove() issue is.  So that the reason for
                        // failSafeRemove here, which is less efficient, but
                        // guarantees to get the job done.
                        assert failSafeRemove(target, objToRemove)
                    }
                    //log.warn("AFTER REMOVAL, TARGET=" + target)
                    addClosure(objToAdd)
                    //log.warn("AFTER ADD-BACK, TARGET=" + target)
                    if (flushMode == FlushMode.FLUSH_DELETES_AND_ADDS) {
                        objToAdd.save()
                        didAdd = true
                    }
                } else {
                    //log.warn("ADDING $objToAdd")
                    addClosure(objToAdd)
                    if (flushMode == FlushMode.FLUSH_DELETES_AND_ADDS) {
                        objToAdd.save()
                        didAdd = true
                    }
                }
            } else {
                //log.warn("SOURCE CONTAINS $objToAdd, LEAVING ALONE")
            }
        }

        if (flushMode == FlushMode.FLUSH_DELETES_AND_ADDS && didAdd && obj) {
            obj.withSession { session ->
                session.flush()
            }
        }

        //log.warn("FINAL TARGET: $target")
    }

    /**
     * Synchronize target collection to match source collection.  After this
     * call, target will only contain what's in source.
     *
     * If flushMode==FlushMode.FLUSH_DELETES, this will first delete any
     * domain object removed from the target collection and then flush the
     * Hibernate session before any new object is added.
     *
     * <p/>
     *
     * Since we're dealing with generic objects of unknown type, the objects
     * must implement the same semantics used in a regular Map to determine
     * equality.  If you look at the Map interface JavaDoc, the
     * specification states that equality in a map is determined by:
     * (key==null ?  k==null : key.equals(k)).
     */
    static <T> void sync(def obj,
                         Collection<T> target,
                         Collection<T> source,
                         FlushMode flushMode,
                         Closure<Boolean> addClosure = null,
                         Closure<Boolean> removeClosure = null) {
        _sync(obj, target, source, flushMode,
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

    static <T> boolean logicallyEquivalent(Collection<T> c1, Collection<T> c2) {
        if (c1?.size() != c2?.size())
            return false;

        long c1HashCode = 0
        c1?.each { c1HashCode += it.hashCode() }

        long c2HashCode = 0
        c2?.each { c2HashCode += it.hashCode() }

        return c1HashCode == c2HashCode
    }

    private static <T> boolean failSafeRemove(Collection<T> c, T val) {
        boolean removed = false
        ArrayList<T> tmp = new ArrayList<T>(c.size())
        for (T element : c) {
            if ((element == null ? val != null : !element.equals(val))) {
                tmp.add(element)
            } else {
                removed = true
            }
        }
        c.clear()
        c.addAll(tmp)
        return removed
    }
}
