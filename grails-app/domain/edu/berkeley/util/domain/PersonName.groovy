package edu.berkeley.util.domain

class PersonName {

    Long id
    NameType nameType
    String fullName

    static belongsTo = [person: Person]

    static constraints = {
        fullName nullable: true
    }

    static mapping = {
        table name: "PersonName"
        version false
        id column: 'id', generator: 'sequence', params: [sequence: 'PersonName_seq'], sqlType: 'BIGINT'
        person column: 'uid', sqlType: 'VARCHAR(64)'
        nameType column: 'nameTypeId', sqlType: 'SMALLINT'
        fullName column: 'fullName', sqlType: 'VARCHAR(255)'
    }
}
