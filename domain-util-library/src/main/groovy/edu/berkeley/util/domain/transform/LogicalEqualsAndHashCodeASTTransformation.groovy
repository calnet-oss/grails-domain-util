package edu.berkeley.util.domain.transform

import edu.berkeley.util.domain.DomainLogicalComparator
import edu.berkeley.util.domain.LogicalEqualsAndHashCodeInterface
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.FieldExpression
import org.codehaus.groovy.ast.expr.VariableExpression
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
 * org.codehaus.groovy.transform.EqualsAndHashCodeASTTransformation
 *
 * Used in conjunction with the @LogicalEqualsAndHashCode annotation to add
 * logicalEquals() and logicalHashCode() to an annotated class.
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class LogicalEqualsAndHashCodeASTTransformation extends AbstractASTTransformation {
    static final Class MY_CLASS = LogicalEqualsAndHashCode.class;
    static final ClassNode MY_TYPE = make(MY_CLASS);
    static final String MY_TYPE_NAME = "@" + MY_TYPE.getNameWithoutPackage();
    private static final ClassNode OBJECT_TYPE = makeClassSafe(Object.class);
    private static final ClassNode EVAL_TYPE = makeClassSafe(Eval.class);
    private static final ClassNode STRING_TYPE = makeClassSafe(String.class);
    private static final ClassNode LIST_TYPE = makeClassSafeWithGenerics(List.class, STRING_TYPE);
    private static final ClassNode DOMAINLOGICALCOMPARATOR_TYPE = make(DomainLogicalComparator.class)
    private static final ClassNode INTERFACE_TYPE = make(LogicalEqualsAndHashCodeInterface.class)
    private static final String EXCLUDES_FIELD = "logicalHashCodeExcludes"
    private static final String INCLUDES_FIELD = "logicalHashCodeIncludes"

    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source);
        AnnotatedNode parent = (AnnotatedNode) nodes[1];
        AnnotationNode anno = (AnnotationNode) nodes[0];
        if (!MY_TYPE.equals(anno.getClassNode())) return;

        if (parent instanceof ClassNode) {
            ClassNode cNode = (ClassNode) parent;
            if (!checkNotInterface(cNode, MY_TYPE_NAME)) return;
            List<String> excludes = getMemberList(anno, "excludes");
            List<String> includes = getMemberList(anno, "includes");
            if (hasAnnotation(cNode, CanonicalASTTransformation.MY_TYPE)) {
                AnnotationNode canonical = cNode.getAnnotations(CanonicalASTTransformation.MY_TYPE).get(0);
                if (excludes == null || excludes.isEmpty()) excludes = getMemberList(canonical, "excludes");
                if (includes == null || includes.isEmpty()) includes = getMemberList(canonical, "includes");
            }
            if (!checkIncludeExclude(anno, excludes, includes, MY_TYPE_NAME)) return;
            createIncludeExcludeFields(cNode, excludes, includes);
            createHashCode(cNode);
            createEquals(cNode);
            // add implements LogicalEqualsAndHashCodeInterface
            addInterface(cNode);
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
        boolean hasExistingField = cNode.getDeclaredField(fieldName);
        if (hasExistingField && cNode.getDeclaredField("_$fieldName")) return;

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

    public static void createHashCode(ClassNode cNode) {
        // make a public method if none exists otherwise try a private
        // method with leading underscore
        boolean hasExistingHashCode = hasDeclaredMethod(cNode, "logicalHashCode", 0);
        if (hasExistingHashCode && hasDeclaredMethod(cNode, "_logicalHashCode", 0)) return;

        // method body
        final BlockStatement body = new BlockStatement();
        body.addStatement(createHashStatements(cNode))

        // add method to class
        cNode.addMethod(new MethodNode(
                hasExistingHashCode ? "_logicalHashCode" : "logicalHashCode",
                hasExistingHashCode ? ACC_PRIVATE : ACC_PUBLIC,
                ClassHelper.int_TYPE, // returnType
                Parameter.EMPTY_ARRAY, // parameters
                ClassNode.EMPTY_ARRAY, // exceptions
                body
        ))
    }

    // logicalHashCode() statements
    private static Statement createHashStatements(ClassNode cNode) {
        final BlockStatement body = new BlockStatement();

        /**
         * Add the following code:
         * def _result
         * _result = DomainLogicalComparator.logicalHashCode(this, logicalHashCodeIncludes, logicalHashCodeExcludes)
         * return _result
         */

        final Expression _result = varX("_result")
        body.addStatement(declS(_result,
                callX(
                        DOMAINLOGICALCOMPARATOR_TYPE,
                        "logicalHashCode",
                        args(
                                varX("this"), includesExpr(cNode), excludesExpr(cNode)
                        )
                )
        ))
        body.addStatement(returnS(_result))

        return body
    }

    public static void createEquals(ClassNode cNode) {
        // make a public method if none exists otherwise try a private
        // method with leading underscore
        boolean hasExistingEquals = hasDeclaredMethod(cNode, "logicalEquals", 0);
        if (hasExistingEquals && hasDeclaredMethod(cNode, "_logicalEquals", 0)) return;

        // parameter to logicalEquals()
        VariableExpression objVar = varX("obj")

        // method body
        final BlockStatement body = new BlockStatement();
        body.addStatement(createEqualsStatements(cNode, objVar))

        // add method to class
        cNode.addMethod(new MethodNode(
                hasExistingEquals ? "_logicalEquals" : "logicalEquals",
                hasExistingEquals ? ACC_PRIVATE : ACC_PUBLIC,
                ClassHelper.boolean_TYPE, // returnType
                params(param(OBJECT_TYPE, objVar.getName())), // parameters
                ClassNode.EMPTY_ARRAY, // exceptions
                body
        ))
    }

    private static Statement createEqualsStatements(ClassNode cNode, VariableExpression objVar) {
        final BlockStatement body = new BlockStatement();

        /**
         * Add the following code:
         *
         * def _result
         * _result = DomainLogicalComparator.compare(this, obj, logicalHashCodeIncludes, logicalHashCodeExcludes)
         * return _result == 0
         */

        final Expression _result = varX("_result")
        body.addStatement(declS(_result,
                callX(
                        DOMAINLOGICALCOMPARATOR_TYPE,
                        "compare",
                        args(
                                varX("this"), objVar, includesExpr(cNode), excludesExpr(cNode)
                        )
                )
        ))
        body.addStatement(returnS(eqX(_result, new ConstantExpression(0, true))))

        return body
    }

    public static void addInterface(ClassNode cNode) {
        cNode.addInterface(INTERFACE_TYPE)
    }
}
