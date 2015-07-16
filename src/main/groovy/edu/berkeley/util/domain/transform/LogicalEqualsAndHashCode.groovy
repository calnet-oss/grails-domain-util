package edu.berkeley.util.domain.transform

import org.codehaus.groovy.transform.GroovyASTTransformationClass

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Usage:
 *
 * @LogicalEqualsAndHashCode
 * <code>
 * class MyClass {
 *     ...
 * }
 * </code>
 *
 * This will add logicalEquals() and a logicalHashCode() methods to a domain
 * class.  These methods will use the DomainLogicalComparator to logically
 * compare domain objects.
 *
 * A logical comparison means comparing values in the domain objects except
 * for the primary keys.
 *
 * This annotation accepts the following parameters:
 *
 * excludes=[list of strings] - Optionally pass a list of field names that
 * should be excluded from comparison.
 *
 * includes=[list of strings] - Optionally pass a list of field names that
 * should only be included in the comparison.  (The primary key field will
 * be ignored if it's included.)
 *
 * @see edu.berkeley.util.domain.DomainLogicalComparator for more
 * information on the logical comparison.
 */
@java.lang.annotation.Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE])
@GroovyASTTransformationClass("edu.berkeley.util.domain.transform.LogicalEqualsAndHashCodeASTTransformation")
@interface LogicalEqualsAndHashCode {
    // fields to exclude from logical equals and hash code
    String[] excludes() default []

    // fields to include for logical equals and hash code
    String[] includes() default []
}
