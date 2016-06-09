package edu.berkeley.util.domain

import grails.test.spock.IntegrationSpec
import edu.berkeley.util.domain.test.*

class DomainCollectionSyncSpec extends IntegrationSpec {

    private static def opts = [failOnError: true, flush: true]

    private void createNameTypes() {
        [
                [id: 1, typeName: "testType1"],
                [id: 2, typeName: "testType2"],
                [id: 3, typeName: "testType3"],
        ].each {
            NameType nt = new NameType(it)
            nt.id = it.id
            nt.save(opts)
        }
    }

    private void createPeople() {
        Person person = new Person(uid: "1", dateOfBirthMMDD: "0101", dummyField: "dummy")
        person.save(opts)
    }

    private void createOriginalPersonNames() {
        Person person = Person.get("1")
        [
                [id: 1, nameType: NameType.get(1), fullName: "John M Smith"],
                [id: 2, nameType: NameType.get(2), fullName: "John Mark Smith"],
                [id: 11, nameType: NameType.get(1), fullName: "John M Typo Smith"],
                [id: 22, nameType: NameType.get(2), fullName: "John Mark Typo Smith"]
        ].each {
            it.person = person
            PersonName name = new PersonName(it)
            name.id = it.id
            person.addToNames(name)
            name.save(failOnError: true)
        }
        person.save(opts)
    }

    void setup() {
        createNameTypes()
        createPeople()
        createOriginalPersonNames()
    }

    // same as the old collection, except we removed 11 and 22 and added 3
    private HashSet<PersonName> getNameCollectionNew(Person person) {
        HashSet<PersonName> set = new HashSet<PersonName>();
        set.add(PersonName.get(1))
        set.add(PersonName.get(2))
        PersonName newName = new PersonName(person: person, nameType: NameType.get(3), fullName: "John Markus Smith")
        newName.id = 3
        newName.save(opts)
        set.add(newName)
        return set
    }

    boolean testCollection(Person person, Closure<Collection> realValuesClosure, Collection expectedValues) {
        Person.withNewSession {
            person = person.refresh()
            // We should have 1,2,3 now, but it can be ordered any way in
            // the set so sort the results
            assert realValuesClosure(person) == expectedValues
        }
        return true
    }

    /**
     * Test that the "target" collection becomes the "source" collection
     * using CollectionUtil.sync().  This utilizes logicalEquals().
     */
    void "test syncing collection using logicalEquals()"() {
        given:
        Person person = Person.get("1")
        when:
        assert person.names*.id.sort() == [1, 2, 11, 22]
        // person.names should contain the new collection
        CollectionUtil.sync(person, person.names, getNameCollectionNew(person), CollectionUtil.FlushMode.NO_FLUSH)
        person.save(opts)
        then:
        // We should have 1,2,3 now, but it can be ordered any way in
        // the set so sort the results
        testCollection(person, { _person -> _person.names*.id.sort() }, [1, 2, 3])
    }

    /**
     * Test that the "target" collection becomes the "source" collection
     * using CollectionUtil.sync().  This utilizes logicalEquals() and
     * an add and remove closure.
     */
    void "test syncing collection using logicalEquals() and add and remove closures"() {
        given:
        Person person = Person.get("1")
        when:
        assert person.names*.id.sort() == [1, 2, 11, 22]
        // person.names should contain the new collection
        CollectionUtil.sync(person, person.names, getNameCollectionNew(person), CollectionUtil.FlushMode.NO_FLUSH, {
            // add closure
            person.addToNames(it)
        }, {
            // remove closure
            person.removeFromNames(it)
        })
        person.save(opts)
        then:
        // We should have 1,2,3 now, but it can be ordered any way in
        // the set so sort the results
        testCollection(person, { _person -> _person.names*.id.sort() }, [1, 2, 3])
    }

    void "test changing a collection that has a unique constraint"() {
        given:
        Person person = Person.get("1")
        UniqueElement ue = new UniqueElement(name: "test1", person: person)
        person.addToUniqueElements(ue)
        person.save(failOnError: true, flush: true)
        person = person.refresh()

        when:
        assert ue.id && person.uniqueElements?.size() == 1
        // create a new collection with a different unique element
        HashSet<UniqueElement> newCollection = new HashSet<UniqueElement>()
        newCollection.add(new UniqueElement(name: "test2", person: person))
        CollectionUtil.sync(person, person.uniqueElements, newCollection, CollectionUtil.FlushMode.FLUSH)
        person.save(failOnError: true, flush: true)
        Closure<Boolean> testResult = {
            Person.withNewSession {
                person = person.refresh()
                assert person.uniqueElements.size() == 1
                assert person.uniqueElements[0].name == "test2"
            }
            return true
        }

        then:
        testResult()
    }
}
