package edu.berkeley.util.domain

import grails.util.Environment

class DomainUtil {
    /**
     * Needed because of GRAILS-11600 'unique' bug, which affects any
     * property in a domain class that is marked 'unique' in the static
     * constraints section of the domain class and while running in test
     * mode using the H2 database and while letting Grails/Hibernate create
     * the DDL automatically for the database.
     *
     * See https://jira.grails.org/browse/GRAILS-11600.
     *
     * This may affect other databases other than H2 as well, but we get
     * around the problem with our production database because we don't
     * allow Grails to create the DDL for us.
     *
     * The issue is this: The DDL generator will generate the same
     * constraint name for properties named the same in different tables.
     *
     * For example, if you have doman class A with a 'hello' property marked
     * unique, and a domain class B with also a 'hello' property marked as
     * unique, you'll probably hit the GRAILS-11600 bug if you are using H2
     * and letting Grails create the DDL.
     *
     * We work around this problem by appending the tableName to the
     * columnName when the Environment is the TEST environment (which is H2
     * with DDL creation enbled).  In another other Environment, the
     * columnName is left alone and is returned as-is.
     *
     * If writing any direct SQL, obviously those SQL statements will have
     * to use the column name as returned from this method as well.  It
     * makes sense to add a getter method in your domain class to get the
     * column name, so that callers writing direct SQL and call the getter
     * in your domain class to get the correct column name.
     */
    static String testSafeColumnName(String tableName, String columnName) {
        return (Environment.getCurrent() == Environment.TEST ? "${columnName}_${tableName}Tbl" : columnName)
    }
}
