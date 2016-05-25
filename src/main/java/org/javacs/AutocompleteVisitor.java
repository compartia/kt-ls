package org.javacs;

import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Type;
import com.google.common.base.Joiner;
import com.sun.source.tree.*;
import com.sun.tools.javac.api.JavacScope;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import io.typefox.lsapi.CompletionItem;
import io.typefox.lsapi.CompletionItemImpl;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;
import javax.tools.JavaFileObject;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AutocompleteVisitor extends CursorScanner {
    private static final Logger LOG = Logger.getLogger("main");
    public static final Pattern REMOVE_PACKAGE_NAME = Pattern.compile("(?:\\w+\\.)+(.*)");
    public final Set<CompletionItem> suggestions = new LinkedHashSet<>();

    public AutocompleteVisitor(JavaFileObject file, long cursor, Context context) {
        super(file, cursor, context);
    }

    /**
     * [expression].[identifier]
     */
    @Override
    public void visitSelect(JCTree.JCFieldAccess node) {
        // If expression contains cursor, no autocomplete
        JCTree.JCExpression expression = node.getExpression();

        if (containsCursor(expression))
            super.visitSelect(node);
        else {
            TypeMirror type = expression.type;

            if (type == null)
                LOG.warning("No type for " + expression);
            else if (isClassReference(expression)) {
                CompletionItemImpl item = new CompletionItemImpl();

                item.setKind(CompletionItem.KIND_PROPERTY);
                item.setLabel("class");
                item.setInsertText("class");

                suggestions.add(item);

                type.accept(new CollectStatics(), null);
            }
            else
                type.accept(new CollectVirtuals(), null);
        }
    }

    private boolean isClassReference(ExpressionTree expression) {
        return expression instanceof JCTree.JCIdent &&
               ((JCTree.JCIdent) expression).sym instanceof Symbol.ClassSymbol;
    }

    @Override
    public void visitReference(JCTree.JCMemberReference node) {
        // If expression contains cursor, no autocomplete
        JCTree.JCExpression expression = node.getQualifierExpression();

        if (containsCursor(expression))
            super.visitReference(node);
        else {
            TypeMirror type = expression.type;

            if (type == null)
                LOG.warning("No type for " + expression);
            else if (isClassReference(expression)) {
                CompletionItemImpl item = new CompletionItemImpl();

                item.setKind(CompletionItem.KIND_METHOD);
                item.setLabel("new");
                item.setInsertText("new");

                suggestions.add(item);

                type.accept(new CollectStatics(), null);
            }
            else
                type.accept(new CollectVirtuals(), null);
        }
    }

    @Override
    public void visitIdent(JCTree.JCIdent node) {
        super.visitIdent(node);

        JavacTrees trees = JavacTrees.instance(context);
        TreePath path = trees.getPath(compilationUnit, node);

        if (path != null) {
            JavacScope scope = trees.getScope(path);

            while (scope != null) {
                LOG.info(Joiner.on(", ").join(scope.getLocalElements()));

                for (Element e : scope.getLocalElements())
                    addElement(e);
                // TODO add to suggestions

                scope = scope.getEnclosingScope();
            }
        }
        else {
            LOG.info("Node " + node + " not found in compilation unit " + compilationUnit);
        }
    }

    private void addElement(Element e) {
        String name = e.getSimpleName().toString();

        switch (e.getKind()) {
            case PACKAGE:
                break;
            case ENUM:
            case CLASS:
            case ANNOTATION_TYPE:
            case INTERFACE:
            case TYPE_PARAMETER: {
                CompletionItemImpl item = new CompletionItemImpl();

                item.setKind(CompletionItem.KIND_INTERFACE);
                item.setLabel(name);
                item.setInsertText(name);

                suggestions.add(item);

                break;
            }
            case ENUM_CONSTANT:
                addEnumConstant(e);

                break;
            case FIELD:
                addField((Symbol.VarSymbol) e);

                break;
            case PARAMETER:
            case LOCAL_VARIABLE:
            case EXCEPTION_PARAMETER: {
                CompletionItemImpl item = new CompletionItemImpl();

                item.setKind(CompletionItem.KIND_VARIABLE);
                item.setLabel(name);
                item.setInsertText(name);

                suggestions.add(item);

                break;
            }
            case METHOD:
                addMethod((Symbol.MethodSymbol) e);

                break;
            case CONSTRUCTOR:
                // TODO
                break;
            case STATIC_INIT:
                // Nothing user-enterable
                break;
            case INSTANCE_INIT:
                // Nothing user-enterable
                break;
            case OTHER:
                break;
            case RESOURCE_VARIABLE:
                break;
        }
    }

    private void addEnumConstant(Element e) {
        String name = e.getSimpleName().toString();
        CompletionItemImpl item = new CompletionItemImpl();

        item.setKind(CompletionItem.KIND_ENUM);
        item.setLabel(name);
        item.setInsertText(name);
        item.setDetail(e.getEnclosingElement().getSimpleName().toString());

        suggestions.add(item);
    }

    private void addMethod(Symbol.MethodSymbol e) {
        String name = e.getSimpleName().toString();
        String params = e.getParameters().stream().map(p -> shortName(p)).collect(Collectors.joining(", "));
        String label = name + "(" + params + ")";

        CompletionItemImpl item = new CompletionItemImpl();

        item.setKind(CompletionItem.KIND_METHOD);
        item.setLabel(label);
        item.setInsertText(name);
        item.setDetail(e.getEnclosingElement().getSimpleName().toString());
        item.setDocumentation(docstring(e));

        suggestions.add(item);
    }

    private String shortName(Symbol.VarSymbol p) {
        String type = shortTypeName(p.type);
        String name = p.name.toString();

        if (name.matches("arg\\d+"))
            return type;
        else
            return type + " " + name;
    }

    private static String shortTypeName(Type type) {
        String longName = type.toString();
        Matcher matcher = REMOVE_PACKAGE_NAME.matcher(longName);

        if (matcher.matches())
            return matcher.group(1);
        else
            return longName;
    }

    private String docstring(Symbol symbol) {
        JavacTrees trees = JavacTrees.instance(context);
        TreePath path = trees.getPath(symbol);

        if (path != null)
            return trees.getDocComment(path);
        else
            return null;
    }

    private void addField(Symbol.VarSymbol e) {
        String name = e.getSimpleName().toString();

        CompletionItemImpl item = new CompletionItemImpl();

        item.setKind(CompletionItem.KIND_METHOD);
        item.setLabel(name);
        item.setInsertText(name);
        item.setDetail(e.getEnclosingElement().getSimpleName().toString());
        item.setDocumentation(docstring(e));

        suggestions.add(item);
    }

    private class CollectStatics extends BridgeTypeVisitor {

        @Override
        public void visitDeclared(DeclaredType t) {
            TypeElement typeElement = (TypeElement) t.asElement();
            List<? extends Element> members = JavacElements.instance(context).getAllMembers(typeElement);

            for (Element e : members) {
                switch (e.getKind()) {
                    case FIELD:
                        Symbol.VarSymbol field = (Symbol.VarSymbol) e;

                        if (field.isStatic())
                            addField(field);

                        break;
                    case METHOD:
                        Symbol.MethodSymbol method = (Symbol.MethodSymbol) e;

                        if (method.isStatic())
                            addMethod(method);

                        break;
                }
            }
        }
    }

    private class CollectVirtuals extends BridgeTypeVisitor {
        @Override
        public void visitArray(ArrayType t) {
            // Array types just have 'length'
            CompletionItemImpl item = new CompletionItemImpl();

            item.setLabel("length");
            item.setInsertText("length");
            item.setKind(CompletionItem.KIND_PROPERTY);

            suggestions.add(item);
        }

        @Override
        public void visitTypeVariable(TypeVariable t) {
            visit(t.getUpperBound());
        }

        @Override
        public void visitDeclared(DeclaredType t) {
            TypeElement typeElement = (TypeElement) t.asElement();
            List<? extends Element> members = JavacElements.instance(context).getAllMembers(typeElement);

            for (Element e : members) {
                switch (e.getKind()) {
                    case FIELD:
                        Symbol.VarSymbol field = (Symbol.VarSymbol) e;

                        if (!field.isStatic())
                            addField(field);

                        break;
                    case METHOD:
                        Symbol.MethodSymbol method = (Symbol.MethodSymbol) e;

                        if (!method.isStatic())
                            addMethod(method);

                        break;
                }
            }
        }

        @Override
        public void visitWildcard(WildcardType t) {
            visit(t.getExtendsBound());
        }
    }
}