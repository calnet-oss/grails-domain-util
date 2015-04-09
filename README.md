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
class MyDomainClass {
    ...
}

```

Then to logically compare two instances of `MyDomainClass`:
```
  boolean isLogicallyEqual = myDomainInstance1.logicallyEquals(myDomainInstance2)
```
