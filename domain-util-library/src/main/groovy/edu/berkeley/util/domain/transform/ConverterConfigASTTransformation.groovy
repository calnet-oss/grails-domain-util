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
    private static final String EXCLUDES_FIELD = "converterConfigExcludes"
    private static final String INCLUDES_FIELD = "converterConfigIncludes"
    private static final String EXCLUDES_GETTER = "getExcludes"
    private static final String INCLUDES_GETTER = "getIncludes"

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
            if (hasAnnotation(cNode, CanonicalASTTransformation.MY_TYPE)) {
                AnnotationNode canonical = cNode.getAnnotations(CanonicalASTTransformation.MY_TYPE).get(0)
                if (excludes == null || excludes.isEmpty()) excludes = getMemberList(canonical, "excludes")
                if (includes == null || includes.isEmpty()) includes = getMemberList(canonical, "includes")
            }
            if (!checkIncludeExclude(anno, excludes, includes, MY_TYPE_NAME)) return
            // add the private static final List<String> fields containing
            // what the include/exclude annotation parameters hold
            createIncludeExcludeFields(cNode, excludes, includes)
            // add the public getIncludes() and getExcludes() methods
            createIncludeExcludeGetters(cNode)
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
