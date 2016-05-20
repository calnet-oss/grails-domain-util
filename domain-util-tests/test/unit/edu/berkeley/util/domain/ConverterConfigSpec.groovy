package edu.berkeley.util.domain

import spock.lang.Specification
import edu.berkeley.util.domain.test.*

class ConverterConfigSpec extends Specification {

    void "test @ConverterConfig instance implements IncludesExcludesInterface"() {
        given:
            // dummyField is excluded in the annotation
            Person person1 = new Person(uid: "1")
            PersonName personName = new PersonName(id: 1, nameType: new NameType(id: 1, typeName: "testType1"), fullName: "John M Smith")
        expect:
            person1 instanceof IncludesExcludesInterface
            personName instanceof IncludesExcludesInterface
    }

    void "test @ConverterConfig excludes"() {
        given:
            Person person1 = new Person(uid: "1", dateOfBirthMMDD: "0101")
        expect:
            person1.excludes == ["dummyField"]
            !person1.includes.size()
    }

    void "test @ConverterConfig includes"() {
        given:
            PersonName personName = new PersonName(id: 1, nameType: new NameType(id: 1, typeName: "testType1"), fullName: "John M Smith")
        expect:
            personName.includes == ["version", "id", "nameType", "fullName"]
            !personName.excludes.size()
    }

    void "test @ConverterConfig includeNulls"() {
        given:
        PersonName personName = new PersonName(id: 1, nameType: new NameType(id: 1, typeName: "testType1"), fullName: "John M Smith")
        expect:
        personName.includeNulls == false
    }
}
