package edu.berkeley.util.domain.test

import edu.berkeley.calnet.groovy.transform.LogicalEqualsAndHashCode

@LogicalEqualsAndHashCode
class NameType {

    long version
    Integer id
    String typeName

    static constraints = {
        typeName unique: true
    }

    static mapping = {
        table name: "NameType"
        version false
        id column: 'id', generator: 'assigned', sqlType: 'SMALLINT'
        typeName column: 'typeName', sqlType: 'VARCHAR(64)'
    }
}
