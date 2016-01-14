package edu.berkeley.util.domain

import groovy.util.logging.Slf4j
import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * A "logical comparison" on a domain class instance means comparing every
 * property except for the primary key property (e.g., 'id').  Disregarding
 * the primary key property is only useful in select cases, probably when
 * comparing one-to-many or many-to-many collections where the identifier
 * may only be a convenience identifier to cover a composite key in the rest
 * of the domain object.  An example of this would be a "PersonName" where
 * the id is a convenience identifier for the logical composite key, which
 * is (uid, nameTypeId).
 *
 * If you compare two domain instances and every property but the primary
 * key is the same, then the comparator will indicate equality.
 *
 * This implementation expects the domain class to be GORM mapped using the
 * "static mapping" field of the domain class.  If the mapping field is
 * missing, an exception will be thrown.
 */
@Slf4j
class DomainLogicalComparator<T> implements Comparator<T> {

    List<String> includes
    List<String> excludes

    /**
     *  HashCodeBuilder will build up a hashCode based on multiple
     *  properties that are passed into the builder.
     */
    static class HashCodeBuilder {
        private int hash

        public int toHashCode() {
            return hash
        }

        /**
         * Add to the hash by appending a property's hashCode.
         *
         * @param o Object whose hash value will be appended
         * @param addendum An optional value to add to the object's hash
         */
        public void append(Object o, Integer addendum) {
            /**
             * We need to "hash the hash" because when we add hash codes
             * like this we need to be very careful with our algorithm.
             *
             * We need to avoid things like this:
             * "a".hashCode() + "d".hashCode() == 197
             * "b".hashCode() + "c".hashCode() == 197
             *
             * "a"+"d" is very different by "b"+"c" but would produce the
             * same hash value if simply added together individually.
             *
             * We solve this problem by taking a MD5 hash of the hash:
             * i.e., a "hash of a hash."
             */
            hash += hashInt(o.hashCode() + (addendum != null ? addendum : 0))
        }

        /**
         * Produce a hash for an integer using MD5 and XOR.  Since MD5 is 16
         * bytes, and we need an integer hash, we XOR the four MD5 4-byte
         * words to produce the final 4-byte hash.
         */
        int hashInt(Integer i) {
            MessageDigest digest = MessageDigest.getInstance("MD5")
            // produce an MD5 digest of the input integer
            digest.update(ByteBuffer.allocate(4).putInt(i).array());
            // MD5 is 16 bytes.  Put it into a byte buffer.
            ByteBuffer digestBuf = ByteBuffer.allocate(16)
            digestBuf.put(digest.digest())
            // XOR the digest in 4 byte chunks to get a single integer
            int hash = digestBuf.getInt(0)
            for (int x = 4; x <= 12; x += 4) {
                hash ^= digestBuf.getInt(x)
            }
            return hash
        }
    }

    /**
     * @return true if Object o is a domain class instance
     */
    protected static boolean isDomain(Object o) {
        return DomainClassArtefactHandler.isDomainClass(o.getClass())
    }

    public static int logicalHashCode(T o1, List<String> _includes = null, List<String> _excludes = null) {
        if (o1 == null)
            return 0
        Map<Integer, Boolean> visitMap = [:]
        HashCodeBuilder hcb = new HashCodeBuilder()
        logicalHashCode(hcb, visitMap, o1, _includes, _excludes, 0)
        return hcb.toHashCode()
    }

    /**
     * Produce a "logical hash code" of the o1 object.  We define our
     * logical hash code to be an aggregate hash value of everything in the
     * object except domain instance identifiers.  We can this make
     * comparison on domain instances sans their row identifier.  When this
     * method exits, HashCodeBuilder will have been updated (if the input
     * object isn't null).
     *
     * @param hcb An instance of HashCodeBuilder.  HashCodeBuilder instances
     *        cannot be reused.
     * @param visitMap An internal map to track which objects have already
     *        been visited in order to prevent circular reference loops.
     * @param o1 The object to produce a logical hash code for.
     */
    protected static void logicalHashCode(HashCodeBuilder hcb, Map<Integer, Boolean> visitMap, T o1, List<String> includes, List<String> excludes, int depth) {
        if (o1 == null) {
            log.trace(getIndentation(depth) + "object is null")
            return
        }

        // Prevent circular references by skipping those objects we've
        // already visited.  Important to use System.identityHashCode() as
        // the object's identity and the key to the map.
        int nativeIdentity = System.identityHashCode(o1)
        if (visitMap.containsKey(nativeIdentity)) {
            log.trace(getIndentation(depth) + "object $nativeIdentity already visited")
            return
        }
        visitMap[nativeIdentity] = true

        if (o1 instanceof Collection) {
            for (def val in o1) {
                logicalHashCode(hcb, visitMap, val, getObjectIncludes(val), getObjectExcludes(val), depth + 1)
            }
        } else {
            DefaultGrailsDomainClass domainClass = new DefaultGrailsDomainClass(o1.getClass())
            for (def property in domainClass.getPersistentProperties()) {
                String propertyName = property.name
                // skip if it's the identity property in the domain instance
                if (!property.isIdentity()) {
                    if (includes?.size() > 0 && !includes.contains(propertyName)) {
                        log.trace(getIndentation(depth) + "$propertyName is not in the includes for ${o1.getClass().name}")
                        continue
                    }
                    if (excludes?.contains(propertyName)) {
                        log.trace(getIndentation(depth) + "$propertyName is in the excludes for ${o1.getClass().name}")
                        continue
                    }
                    if (o1.properties.containsKey(propertyName)) {
                        def val = o1.properties[propertyName]
                        if (val != null && (val instanceof Collection || isDomain(val))) {
                            // value is a Collection or a domain class that
                            // we'll recursively process
                            log.trace(getIndentation(depth) + "Object ${System.identityHashCode(o1)}, type=${o1.getClass().getName()}: ${propertyName} ${System.identityHashCode(val)}: is a collection or domain object")
                            logicalHashCode(hcb, visitMap, val, getObjectIncludes(val), getObjectExcludes(val), depth + 1)
                        } else if (val != null) {
                            // append the property name and object to the
                            // hash code builder, which will update the hash
                            log.trace(getIndentation(depth) + "Object ${System.identityHashCode(o1)}, type=${o1.getClass().getName()}: ${propertyName}: val=$val, identityHashCode={System.identityHashCode(val)}, hashCode=${val.hashCode()}")
                            hcb.append(val, propertyName.hashCode())
                        }
                        else {
                            log.trace(getIndentation(depth) + "Property $propertyName is null")
                        }
                    }
                    else {
                        log.trace(getIndentation(depth) + "Property $propertyName is not in the properties map of the object")
                    }
                }
                else {
                    log.trace(getIndentation(depth) + "Property $propertyName is an identity property")
                }
            }
        }
    }

    /**
     * For trace logging
     */
    private static String getIndentation(int depth) {
        def spaces = new char[depth * 2]
        Arrays.fill(spaces, ' ' as char)
        return new String(spaces)
    }

    /**
     * Compare two domain objects for logical equality.  Logical equality
     * means everything but the domain instance identifiers are logically
     * equal.
     */
    public static int compare(T o1, T o2, List<String> _includes, List<String> _excludes) {
        log.trace("Comparing ${o1.hashCode()} and ${o2.hashCode()}, includes=${_includes}, excludes=${_excludes}")
        int o1Hash = logicalHashCode(o1, _includes, _excludes)
        int o2Hash = logicalHashCode(o2, _includes, _excludes)
        int result = o1Hash.compareTo(o2Hash)
        log.trace("Hash ${o1.hashCode()}: $o1Hash")
        log.trace("Hash ${o2.hashCode()}: $o2Hash")
        log.trace("Result: ${o1.hashCode()}:$o1Hash and ${o2.hashCode()}:$o2Hash -> ${result}")
        return result
    }

    public int compare(T o1, T o2) {
        return compare(o1, o2, includes, excludes)
    }

    private static List<String> getObjectIncludes(T o) {
        if (o instanceof LogicalEqualsAndHashCodeInterface) {
            return o.logicalHashCodeIncludes
        }
        return null
    }

    private static List<String> getObjectExcludes(T o) {
        if (o instanceof LogicalEqualsAndHashCodeInterface) {
            return o.logicalHashCodeExcludes
        }
        return null
    }
}
