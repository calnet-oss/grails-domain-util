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
 * }
 * </code>
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
