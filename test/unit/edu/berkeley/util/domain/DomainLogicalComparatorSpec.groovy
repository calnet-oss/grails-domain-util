package edu.berkeley.util.domain

import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class DomainLogicalComparatorSpec extends Specification {

    Person person1
    Person person1same
    Person person2
    Person person2same

    private PersonName getName1() {
        return new PersonName(id: "pn1", nameType: new NameType(id: "nt1", typeName: "testType1"), fullName: "John M Smith")
    }

    private PersonName getName2() {
        return new PersonName(id: "pn2", nameType: new NameType(id: "nt2", typeName: "testType2"), fullName: "John Mark Smith")
    }

    def setup() {
        // Build two name sets that are logically equivalent but have
        // different hash codes as returned by Set.hashCode().
        LinkedHashSet nameSet1 = new LinkedHashSet()
        nameSet1.add(getName1())
        nameSet1.add(getName2())
        LinkedHashSet nameSet1same = new LinkedHashSet()
        nameSet1same.add(getName2())
        nameSet1same.add(getName1())

        person1 = new Person(uid: "1", dateOfBirthMMDD: "0101", names: nameSet1)
        person1same = new Person(uid: "1same", dateOfBirthMMDD: "0101", names: nameSet1same)
        person2 = new Person(uid: "2", dateOfBirthMMDD: "0202")
        person2same = new Person(uid: "2same", dateOfBirthMMDD: "0202")
    }

    def cleanup() {
    }

    /**
     *  Test that the Comparator correctly detects logical equality between two different objects.
     */
    void "test comparator equality"() {
        given:
            DomainLogicalComparator<Person> comparator = new DomainLogicalComparator<Person>()
        when:
            int compareResult = comparator.compare(person1, person1same)
        then:
            compareResult == 0
    }

    /**
     *  Test that the Comparator correctly detects logical inequality.
     */
    void "test comparator inequality"() {
        given:
            DomainLogicalComparator<Person> comparator = new DomainLogicalComparator<Person>()
        when:
            int compareResult = comparator.compare(person1, person2)
        then:
            compareResult != 0
    }

    /**
     *  Test that the Comparator can compare objects with circular references in them.
     */
    void "test comparator with circular reference"() {
        given:
            DomainLogicalComparator<Person> comparator = new DomainLogicalComparator<Person>()
            Person person1 = new Person(uid: "1")
            PersonName name = getName1()
            name.person = person1 // make a circular reference back to Person
            person1.names = new HashSet()
            person1.names.add(name)
        when:
            int compareResult = comparator.compare(person1, person1)
        then:
            compareResult == 0
    }
}
