package edu.berkeley.util.domain.transform

import org.codehaus.groovy.transform.GroovyASTTransformationClass

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE])
@GroovyASTTransformationClass("edu.berkeley.util.domain.transform.LogicalEqualsAndHashCodeASTTransformation")
@interface LogicalEqualsAndHashCode {
    String[] excludes() default []

    String[] includes() default []
}
