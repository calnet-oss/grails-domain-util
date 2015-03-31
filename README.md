Grails Domain Utility Plugin
============================

This plugin offers utility classes for handling domain class instances.

Primarily:
  * `DomainLogicalComparator` and `CollectionUtil`
    * Ability to compare collections and add new elements and remove old
      elements based on the comparison of a source collection and a target
      collection.  Useful for adding and removing elements from things like
      one-to-many collections.
