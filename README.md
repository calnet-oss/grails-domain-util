Grails Domain Utility Plugin
============================

This plugin offers utility classes for handling domain class instances.

Primarily:
  * `DomainLogicalComparator` and `CollectionUtil`
    * Ability to compare collections and add new elements and remove old
      elements based on the comparison of a source collection and a target
      collection.  Useful for adding and removing elements from things like
      one-to-many collections.

The easiest way to use `DomainLogicalComparator` is with the
`@LogicalEqualsAndHashCode` annotation.

Example:
```
import edu.berkeley.util.domain.transform.LogicalEqualsAndHashCode

@LogicalEqualsAndHashCode
class Person {
    static hasMany = [ names : PersonName ]
    ...
}

```

Then to logically compare two instances of `Person`:
```
  boolean isLogicallyEqual = person1.logicalEquals(person2)
```

To use the `sync` method from `CollectionUtil`:
```
  CollectionUtil.sync(person1.names, newNamesCollection)
```

Where `newNamesCollection` contains collection of names that are targetted
for the `person1.names` set.  After `sync()`:

 * If there are elements in in `newNamesCollection` that are logically
   already in `person1.names`, those elements in `person1.names` will
   remain.
 * If `newNamesCollection` has elements that `person1.names` logically
   didn't have, `person1.names` will now have those elements.
 * If `person1.names` had elements that `newNamesCollection` logically
   doesn't have, those elements will have been removed from `person1.names`.
