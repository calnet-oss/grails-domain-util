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

import edu.berkeley.util.domain.test.NameType
import edu.berkeley.util.domain.test.Person
import edu.berkeley.util.domain.test.PersonName
import edu.berkeley.util.domain.test.UniqueElement
import grails.test.mixin.integration.Integration
import grails.transaction.Rollback
import spock.lang.Specification

@Integration
@Rollback
class DomainCollectionSyncIntegrationSpec extends Specification {

    private static def opts = [failOnError: true, flush: true]

    /**
     * Test that the "target" collection becomes the "source" collection
     * using CollectionUtil.sync().  This utilizes hashCode() and equals()
     * from LogicalHashCodeAndEquals.
     */
    void "test syncing collection using hashCode() and equals() from LogicalHashCodeAndEquals"() {
        given: "Data is created inside a transaction"
        setupData()

        and: "And the getting the first person"
        Person person = Person.get("1")

        when:
        // person.names should contain the new collection
        assert person.names*.id.sort() == [1L, 2L, 11L, 22L]
        CollectionUtil.sync(person, person.names, getNameCollectionNew(person), CollectionUtil.FlushMode.NO_FLUSH)
        person.save(opts)
        person.refresh()

        then:
        // We should have 1,2,3 now, but it can be ordered any way in
        // the set so sort the results
        testCollection(person, { it.names.collect { name -> "${name.id}:${name.fullName}" }.sort() }, ["1:John M Smith", "2:John Mark Changed", "3:John Markus Smith"])
    }

    /**
     * Test that the "target" collection becomes the "source" collection
     * using CollectionUtil.sync().  This utilizes hashCode() and equals()
     * from LogicalHashCodeAndEquals and an add and remove closure.
     */
    void "test syncing collection using hashCode() and equals() from LogicalHashCodeAndEquals and add and remove closures"() {
        given: "Data is created inside a transaction"
        setupData()

        and: "And the getting the first person"
        Person person = Person.get("1")

        when:
        // person.names should contain the new collection
        assert person.names*.id.sort() == [1L, 2L, 11L, 22L]
        CollectionUtil.sync(person, person.names, getNameCollectionNew(person), CollectionUtil.FlushMode.NO_FLUSH, {
            // add closure
            person.addToNames(it)
        }, {
            // remove closure
            person.removeFromNames(it)
        })
        person.save(opts)
        person.refresh()

        then:
        // We should have 1,2,3 now, but it can be ordered any way in
        // the set so sort the results
        testCollection(person, { it.names.collect { name -> "${name.id}:${name.fullName}" }.sort() }, ["1:John M Smith", "2:John Mark Changed", "3:John Markus Smith"])
    }

    void "test changing a collection that has a unique constraint"() {
        given: "Data is created inside a transaction"
        setupData()

        and: "And the getting the first person"
        Person person = Person.get("1")
        UniqueElement ue = new UniqueElement(name: "test1", person: person)
        person.addToUniqueElements(ue)
        person.save(opts)
        person.refresh()

        when:
        // create a new collection with a different unique element
        assert person.uniqueElements?.size() == 1 && person.uniqueElements[0].id
        HashSet<UniqueElement> newCollection = new HashSet<UniqueElement>()
        newCollection.add(new UniqueElement(name: "test2", person: person))
        CollectionUtil.sync(person, person.uniqueElements, newCollection, CollectionUtil.FlushMode.FLUSH, {
            person.addToUniqueElements(it)
        }, {
            person.removeFromUniqueElements(it)
        })
        person.save(opts)
        person.refresh()

        then:
        person.uniqueElements.size() == 1
        person.uniqueElements[0].name == "test2"
    }

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

    /**
     * Data setup cannot be done in `setup` (see: http://docs.grails.org/latest/guide/testing.html#integrationTesting for reason)
     */
    void setupData() {
        createNameTypes()
        createPeople()
        createOriginalPersonNames()
    }

    // same as the old collection, except we removed 11 and 22 and added 3
    private HashSet<PersonName> getNameCollectionNew(Person person) {
        HashSet<PersonName> set = new HashSet<PersonName>();

        // this will test keeping something
        set.add(PersonName.get(1))

        // this will test replacing something
        PersonName name2 = PersonName.get(2)
        name2.fullName = "John Mark Changed"
        set.add(name2)

        // this will test adding something
        PersonName newName = new PersonName(person: person, nameType: NameType.get(3), fullName: "John Markus Smith")
        newName.id = 3
        newName.save(opts)
        set.add(newName)

        return set
    }

    boolean testCollection(Person person, Closure<Collection> realValuesClosure, Collection expectedValues) {
        // We should have 1,2,3 now, but it can be ordered any way in
        // the set so sort the results
        assert realValuesClosure(person) == expectedValues
        return true
    }
}
