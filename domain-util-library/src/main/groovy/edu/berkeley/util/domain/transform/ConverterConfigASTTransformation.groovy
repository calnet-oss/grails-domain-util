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

import edu.berkeley.util.domain.IncludesExcludesInterface
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.FieldExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.AbstractASTTransformation
import org.codehaus.groovy.transform.CanonicalASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import static org.codehaus.groovy.ast.ClassHelper.make
import static org.codehaus.groovy.ast.tools.GeneralUtils.*
import static org.codehaus.groovy.ast.tools.GenericsUtils.makeClassSafe
import static org.codehaus.groovy.ast.tools.GenericsUtils.makeClassSafeWithGenerics

/**
 * Partially derived from
 * org.codehaus.groovy.transform.EqualsAndHashCodeASTTransformation, which
 * uses includes and excludes.
 *
 * Used in conjunction with the @ConverterConfig annotation to add an
 * extension of the IncludesExcludesInterface to the annotated class and add
 * getIncludes() and getExcludes() methods based on the includes and
 * excludes parameters from the annotation.
 *
 * @see ConverterConfig
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class ConverterConfigASTTransformation extends AbstractASTTransformation {
    static final Class MY_CLASS = ConverterConfig.class
    static final ClassNode MY_TYPE = make(MY_CLASS)
    static final String MY_TYPE_NAME = "@" + MY_TYPE.getNameWithoutPackage()
    private static final ClassNode EVAL_TYPE = makeClassSafe(Eval.class)
    private static final ClassNode STRING_TYPE = makeClassSafe(String.class)
    private static final ClassNode LIST_TYPE = makeClassSafeWithGenerics(List.class, STRING_TYPE)
    private static final ClassNode INTERFACE_TYPE = make(IncludesExcludesInterface.class)
    private static final ClassNode BOOLEAN_TYPE = make(Boolean.class)
    private static final String EXCLUDES_FIELD = "converterConfigExcludes"
    private static final String INCLUDES_FIELD = "converterConfigIncludes"
    private static final String INCLUDE_NULLS_FIELD = "converterIncludeNulls"
    private static final String EXCLUDES_GETTER = "getExcludes"
    private static final String INCLUDES_GETTER = "getIncludes"
    private static final String INCLUDE_NULLS_GETTER = "getIncludeNulls"

    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source)
        AnnotatedNode parent = (AnnotatedNode) nodes[1]
        AnnotationNode anno = (AnnotationNode) nodes[0]
        if (!MY_TYPE.equals(anno.getClassNode())) return

        if (parent instanceof ClassNode) {
            ClassNode cNode = (ClassNode) parent
            if (!checkNotInterface(cNode, MY_TYPE_NAME)) return
            List<String> excludes = getMemberList(anno, "excludes")
            List<String> includes = getMemberList(anno, "includes")
            Boolean includeNulls = getMemberList(anno, "includeNulls")
            if (hasAnnotation(cNode, CanonicalASTTransformation.MY_TYPE)) {
                AnnotationNode canonical = cNode.getAnnotations(CanonicalASTTransformation.MY_TYPE).get(0)
                if (excludes == null || excludes.isEmpty()) excludes = getMemberList(canonical, "excludes")
                if (includes == null || includes.isEmpty()) includes = getMemberList(canonical, "includes")
                if (includeNulls == null) includeNulls = getMemberList(canonical, "includeNulls")
            }

            if (!checkIncludeExclude(anno, excludes, includes, MY_TYPE_NAME)) return

            // add the private static final List<String> fields containing
            // what the include/exclude annotation parameters hold
            createIncludeExcludeFields(cNode, excludes, includes)
            // create the Boolean INCLUDE_NULLS_FIELD field
            createIncludeNullsField(cNode, INCLUDE_NULLS_FIELD, includeNulls)

            // add the public getIncludes() and getExcludes() methods
            createIncludeExcludeGetters(cNode)
            // create the public getIncludeNulls() method
            createIncludeNullsGetter(cNode, INCLUDE_NULLS_GETTER, INCLUDE_NULLS_FIELD)

            // add implements IncludesExcludesInterface
            addInterface(cNode)
        }
    }

    public static void createIncludeExcludeFields(
            ClassNode cNode,
            List<String> excludes,
            List<String> includes
    ) {
        createIncludeExcludeField(cNode, EXCLUDES_FIELD, excludes)
        createIncludeExcludeField(cNode, INCLUDES_FIELD, includes)
    }

    private static void createIncludeExcludeField(ClassNode cNode, String fieldName, List<String> list) {
        boolean hasExistingField = cNode.getDeclaredField(fieldName)
        if (hasExistingField && cNode.getDeclaredField("_$fieldName")) return

        cNode.addField(new FieldNode(
                hasExistingField ? "_$fieldName" : fieldName,
                (hasExistingField ? ACC_PRIVATE : ACC_PUBLIC) | ACC_FINAL | ACC_STATIC,
                LIST_TYPE,
                cNode,
                callX(
                        EVAL_TYPE, "me",
                        args(
                                new ConstantExpression(list.inspect() as String)
                        )
                )
        ))
    }

    private static void createIncludeNullsField(ClassNode cNode, String fieldName, Boolean includeNulls) {
        boolean hasExistingField = cNode.getDeclaredField(fieldName)
        if (hasExistingField && cNode.getDeclaredField("_$fieldName")) return

        cNode.addField(new FieldNode(
                hasExistingField ? "_$fieldName" : fieldName,
                (hasExistingField ? ACC_PRIVATE : ACC_PUBLIC) | ACC_FINAL | ACC_STATIC,
                BOOLEAN_TYPE,
                cNode,
                callX(
                        EVAL_TYPE, "me",
                        args(
                                new ConstantExpression(includeNulls.inspect() as String)
                        )
                )
        ))
    }

    public static FieldExpression includesExpr(ClassNode cNode) {
        return fieldX(cNode, INCLUDES_FIELD)
    }

    public static FieldExpression excludesExpr(ClassNode cNode) {
        return fieldX(cNode, EXCLUDES_FIELD)
    }

    public static void addInterface(ClassNode cNode) {
        cNode.addInterface(INTERFACE_TYPE)
    }

    public static void createIncludeExcludeGetters(ClassNode cNode) {
        createIncludeExcludeGetter(cNode, EXCLUDES_GETTER, EXCLUDES_FIELD)
        createIncludeExcludeGetter(cNode, INCLUDES_GETTER, INCLUDES_FIELD)
    }

    public static void createIncludeExcludeGetter(ClassNode cNode, String methodName, String fieldName) {
        // make a public method if none exists otherwise try a private
        // method with leading underscore
        boolean hasExistingMethod = hasDeclaredMethod(cNode, methodName, 0)
        if (hasExistingMethod && hasDeclaredMethod(cNode, "_$methodName", 0)) return

        // method body
        final BlockStatement body = new BlockStatement()
        body.addStatement(createGetterStatements(cNode, fieldName))

        // add method to class
        cNode.addMethod(new MethodNode(
                hasExistingMethod ? "_$methodName" : methodName,
                hasExistingMethod ? ACC_PRIVATE : ACC_PUBLIC,
                LIST_TYPE, // returnType
                params(), // parameters
                ClassNode.EMPTY_ARRAY, // exceptions
                body
        ))
    }

    public static void createIncludeNullsGetter(ClassNode cNode, String methodName, String fieldName) {
        // make a public method if none exists otherwise try a private
        // method with leading underscore
        boolean hasExistingMethod = hasDeclaredMethod(cNode, methodName, 0)
        if (hasExistingMethod && hasDeclaredMethod(cNode, "_$methodName", 0)) return

        // method body
        final BlockStatement body = new BlockStatement()
        body.addStatement(createGetterStatements(cNode, fieldName))

        // add method to class
        cNode.addMethod(new MethodNode(
                hasExistingMethod ? "_$methodName" : methodName,
                hasExistingMethod ? ACC_PRIVATE : ACC_PUBLIC,
                BOOLEAN_TYPE, // returnType
                params(), // parameters
                ClassNode.EMPTY_ARRAY, // exceptions
                body
        ))
    }

    private static Statement createGetterStatements(ClassNode cNode, String fieldName) {
        final BlockStatement body = new BlockStatement()

        /**
         * Add the following code:
         *
         * return fieldName
         */

        body.addStatement(returnS(varX(fieldName)))

        return body
    }
}
