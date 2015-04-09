package edu.berkeley.util.domain

import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import spock.lang.Specification
import edu.berkeley.util.domain.LogicalEqualsAndHashCodeInterface

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

    private LinkedHashSet getNameSet1() {
        LinkedHashSet nameSet1 = new LinkedHashSet()
        nameSet1.add(getName1())
        nameSet1.add(getName2())
        return nameSet1
    }

    private LinkedHashSet getNameSet1Same() {
        LinkedHashSet nameSet1same = new LinkedHashSet()
        nameSet1same.add(getName2())
        nameSet1same.add(getName1())
        return nameSet1same
    }

    private Person getPerson1() {
        return new Person(uid: "1", dateOfBirthMMDD: "0101", names: getNameSet1())
    }

    private Person getPerson1Same() {
        return new Person(uid: "1same", dateOfBirthMMDD: "0101", names: getNameSet1Same())
    }

    private Person getPerson2() {
        return new Person(uid: "2", dateOfBirthMMDD: "0202")
    }

    private Person getPerson2Same() {
        return new Person(uid: "2same", dateOfBirthMMDD: "0202")
    }

    def setup() {
        // Build two name sets that are logically equivalent but have
        // different hash codes as returned by Set.hashCode().
        LinkedHashSet nameSet1 = getNameSet1()
        LinkedHashSet nameSet1same = getNameSet1Same()

        person1 = getPerson1()
        person1same = getPerson1Same()
        person2 = getPerson2()
        person2same = getPerson2Same()
    }

    def cleanup() {
    }

    /**
     *  Test that the Comparator correctly detects logical equality between
     *  two different objects.
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
     * Test that 'includes' works.  'includes' tells the Comparator which
     * fields to include in the comparison,
     */
    void "test comparator equality with includes"() {
        given:
            Person _person1 = getPerson1()
            Person _person1same = getPerson1Same()
            // set dummy field to be different to make the two objects different
            _person1.dummyField = "A"
            _person1same.dummyField = "B"
            DomainLogicalComparator<Person> comparatorWithoutIncludes = new DomainLogicalComparator<Person>()
            DomainLogicalComparator<Person> comparatorWithIncludes = new DomainLogicalComparator<Person>(includes: ['dateOfBirthMMDD', 'names'])
        when:
            int compareWithoutIncludesResult = comparatorWithoutIncludes.compare(_person1, _person1same)
            int compareWithIncludesResult = comparatorWithIncludes.compare(_person1, _person1same)
        then:
            // without the includes, the comparison should show inequality
            compareWithoutIncludesResult != 0
            // with includes, the comparison should show equality
            compareWithIncludesResult == 0
    }

    /**
     * Test that 'excludes' works.  'excludes' tells the Comparator which
     * fields to exclude from the comparison.
     */
    void "test comparator equality with excludes"() {
        given:
            Person _person1 = getPerson1()
            Person _person1same = getPerson1Same()
            // set dummy field to be different to make the two objects different
            _person1.dummyField = "A"
            _person1same.dummyField = "B"
            DomainLogicalComparator<Person> comparatorWithoutExcludes = new DomainLogicalComparator<Person>()
            DomainLogicalComparator<Person> comparatorWithExcludes = new DomainLogicalComparator<Person>(excludes: ['dummyField'])
        when:
            int compareWithoutExcludesResult = comparatorWithoutExcludes.compare(_person1, _person1same)
            int compareWithExcludesResult = comparatorWithExcludes.compare(_person1, _person1same)
        then:
            // without the excludes, the comparison should show inequality
            compareWithoutExcludesResult != 0
            // with excludes, the comparison should show equality
            compareWithExcludesResult == 0
    }

    void "test @LogicalEqualsAndHashCode excludes field"() {
        given:
            Person person1 = new Person(uid: "1", dateOfBirthMMDD: "0101")
        when:
            List<String> excludes = person1.logicalHashCodeExcludes
        then:
            excludes != null && excludes == ["dummyField"]
    }

    /**
     *  Test equality using the @LogicalEqualsAndHashCode annotation and logicalHashCode()
     */
    void "test comparator equality using @LogicalEqualsAndHashCode and logicalHashCode()"() {
        given:
            // dummyField ie excluded in the annotation
            Person person1 = new Person(uid: "1", dateOfBirthMMDD: "0101", dummyField: "ABC")
            Person person1same = new Person(uid: "1same", dateOfBirthMMDD: "0101", dummyField: "DEF")
        when:
            int compareResult = person1.logicalHashCode().compareTo(person1same.logicalHashCode())
        then:
            compareResult == 0
    }

    /**
     *  Test equality using the @LogicalEqualsAndHashCode annotation and logicalEquals()
     */
    void "test comparator equality using @LogicalEqualsAndHashCode and logicalEquals()"() {
        given:
            // dummyField is excluded in the annotation
            Person person1 = new Person(uid: "1", dateOfBirthMMDD: "0101", dummyField: "ABC")
            Person person1same = new Person(uid: "1same", dateOfBirthMMDD: "0101", dummyField: "DEF")
        when:
            boolean theSame = person1.logicalEquals(person1same)
        then:
            theSame == true
    }

    void "test @LogicalEqualsAndHashCode instance implements LogicalEqualsAndHashCodeInterface()"() {
        given:
            // dummyField is excluded in the annotation
            Person person1 = new Person(uid: "1")
        when:
            boolean implementsInterface = person1 instanceof LogicalEqualsAndHashCodeInterface
        then:
            implementsInterface == true
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
