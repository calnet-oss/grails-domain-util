package edu.berkeley.util.domain.transform

import org.codehaus.groovy.transform.GroovyASTTransformationClass

import java.lang.annotation.*

/**
 * Usage:
 *
 * <code>
 * @ConverterConfig
 * class MyClass {
 *     ...
 *}* </code>
 *
 * This will make the class implement the IncludesExcludes interface, which
 * adds getIncludes() and getExcludes() methods.  These methods return lists
 * that configure the ExtendedJSON converter to include or exclude fields
 * when converting the object to JSON.  By default, both these methods
 * return an empty list, indicating no exclusions nor inclusions.  But if
 * either the 'excludes' or 'includes' annotation parameters are set (only
 * one of them can be set at a time)), then the methods will return the list
 * of fields that are configured.
 *
 * This annotation accepts the following parameters:
 *
 * excludes=[list of strings] - Optionally pass a list of field names that
 * should be excluded.
 *
 * includes=[list of strings] - Optionally pass a list of field names that
 * should only be included.
 *
 * 'excludes' and 'includes' cannot both be set in the same annotation. 
 * Either one of them is set or neither of them are set.
 *
 * includeNulls=true or false - Indicates if null balues should be included
 * in the converter output.  The default is false.
 *
 * @see edu.berkeley.render.json.converters.ExtendedJSON , grails.converters.JSON, grails.converters.AbstractConverter
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE])
@GroovyASTTransformationClass("edu.berkeley.util.domain.transform.ConverterConfigASTTransformation")
@interface ConverterConfig {
    /**
     * fields to exclude from conversion
     */
    String[] excludes() default []

    /**
     * fields to include for conversion
     */
    String[] includes() default []

    /**
     * If true, will add null values to the converter output.
     * Default is false.
     */
    boolean includeNulls() default false
}
