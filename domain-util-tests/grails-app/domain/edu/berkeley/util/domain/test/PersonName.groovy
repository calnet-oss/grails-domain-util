package edu.berkeley.util.domain.test

import edu.berkeley.util.domain.transform.ConverterConfig
import edu.berkeley.calnet.groovy.transform.LogicalEqualsAndHashCode

@LogicalEqualsAndHashCode(excludes = ["person"])
@ConverterConfig(includes = ["version", "id", "nameType", "fullName"])
class PersonName {

    long version
    Long id
    NameType nameType
    String fullName

    Person person
    static belongsTo = [person: Person]

    static constraints = {
        fullName nullable: true
    }

    static mapping = {
        table name: "PersonName"
        version false
        id column: 'id', generator: 'assigned', sqlType: 'BIGINT'
        person column: 'uid', sqlType: 'VARCHAR(64)'
        nameType column: 'nameTypeId', sqlType: 'SMALLINT'
        fullName column: 'fullName', sqlType: 'VARCHAR(255)'
    }
}
