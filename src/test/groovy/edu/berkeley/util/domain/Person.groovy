package edu.berkeley.util.domain

import edu.berkeley.util.domain.transform.LogicalEqualsAndHashCode

@LogicalEqualsAndHashCode(excludes = ["dummyField"])
class Person {

    long version
    String id // uid
    String dateOfBirthMMDD
    String dummyField

    Set<PersonName> names
    Set<UniqueElement> uniqueElements
    static hasMany = [names: PersonName, uniqueElements: UniqueElement]

    static constraints = {
        dateOfBirthMMDD nullable: true
    }

    static mapping = {
        table name: "Person"
        version false
        id name: 'uid', column: 'uid', generator: 'assigned', sqlType: 'VARCHAR(64)'
        dateOfBirthMMDD column: 'dateOfBirthMMDD', sqlType: 'CHAR(4)'
        names cascade: "all-delete-orphan"
        uniqueElements cascade: "all-delete-orphan"
    }

    static transients = ['uid']

    public String getUid() { return id }

    public void setUid(String uid) { this.id = uid }
}
