package edu.berkeley.util.domain

import edu.berkeley.util.domain.transform.LogicalEqualsAndHashCode

@LogicalEqualsAndHashCode
class NameType {

    Integer id
    String typeName

    static constraints = {
        typeName unique: true
    }

    static mapping = {
        table name: "NameType"
        version: false
            id column: 'id', sqlType: 'SMALLINT'
            typeName column: 'typeName', sqlType: 'VARCHAR(64)'
    }
}
