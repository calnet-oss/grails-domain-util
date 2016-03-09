package edu.berkeley.util.domain.test

import edu.berkeley.util.domain.transform.LogicalEqualsAndHashCode

@LogicalEqualsAndHashCode(excludes = ["person"])
class UniqueElement {
    long version
    Long id
    String name

    Person person
    static belongsTo = [person: Person]

    static constraints = {
        person nullable: false, unique: true
    }

    static mapping = {
        table name: "UniqueElement"
        version false
        id column: 'id', generator: 'sequence', params: [sequence: 'UniqueElement_seq'], sqlType: 'BIGINT'
        person column: 'uid', sqlType: 'VARCHAR(64)'
        name column: 'name', sqlType: 'VARCHAR(64)'
    }
}
