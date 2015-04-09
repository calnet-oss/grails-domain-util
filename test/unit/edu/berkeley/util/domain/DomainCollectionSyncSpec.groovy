package edu.berkeley.util.domain

import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class DomainCollectionSyncSpec extends Specification {

    private PersonName newPersonName(Long identifier, NameType nameType, String fullName) {
        PersonName name = new PersonName(nameType: nameType, fullName: fullName)
        name.setId(identifier)
        return name
    }

    private NameType newNameType(Integer identifier, String typeName) {
        NameType nameType = new NameType(typeName: typeName)
        nameType.setId(identifier)
        return nameType
    }

    private HashSet<PersonName> getNameCollectionOld() {
        HashSet<PersonName> set = new HashSet<PersonName>();
        set.add(newPersonName(1, newNameType(1, "testType1"), "John M Smith"))
        set.add(newPersonName(2, newNameType(2, "testType2"), "John Mark Smith"))
        set.add(newPersonName(11, newNameType(1, "testType1"), "John M Typo Smith"))
        set.add(newPersonName(22, newNameType(2, "testType2"), "John Mark Typo Smith"))
        return set
    }

    // same as the old collection, except we removed 11 and 22 and added 3
    private HashSet<PersonName> getNameCollectionNew() {
        HashSet<PersonName> set = new HashSet<PersonName>();
        set.add(newPersonName(1, newNameType(1, "testType1"), "John M Smith"))
        set.add(newPersonName(2, newNameType(2, "testType2"), "John Mark Smith"))
        set.add(newPersonName(3, newNameType(3, "testType3"), "John Markus Smith"))
        return set
    }

    def setup() {
    }

    def cleanup() {
    }

    /**
     * Test that the "target" collection becomes the "source" collection
     * using CollectionUtil.sync().  This utilizes "logical comparison"
     * of domain objects using the DomainLogicialComparator.
     */
    void "test duplicating collection"() {
        given:
            Person person = new Person(uid: "1", dateOfBirthMMDD: "0101")
            person.setNames(getNameCollectionOld())
        when:
            // person.names should contain the new collection
            CollectionUtil.sync(new DomainLogicalComparator<PersonName>(), person.names, getNameCollectionNew())
        then:
            // We should have 1,2,3 now, but it can be ordered any way in the set so sort the results
            person.names*.id.sort() == [1, 2, 3]
    }
}
