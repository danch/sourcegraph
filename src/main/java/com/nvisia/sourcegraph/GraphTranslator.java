package com.nvisia.sourcegraph;

import com.nvisia.sourcegraph.antlr.Java9Parser;
import com.nvisia.sourcegraph.graph.EdgeType;
import com.nvisia.sourcegraph.graph.Node;
import com.nvisia.sourcegraph.graph.NodeRef;
import com.nvisia.sourcegraph.graph.NodeType;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.*;

public class GraphTranslator extends com.nvisia.sourcegraph.antlr.Java9BaseListener {
    private Map<String, Node> typeNodes = new TreeMap<>();
    private ContainerNodeStack containerNodeStack = new ContainerNodeStack();
    private Stack<Scope> scopeStack = new Stack<>();

    public GraphTranslator() {
    }

    public Collection<Node> getTopLevelNodes() {
        return containerNodeStack.getRootNodes();
    }
    public Map<String, Node> getTypeCache() {
        return typeNodes;
    }

    private void dumpChildren(ParserRuleContext ctx) {
        for (int i=0;i<ctx.getChildCount();i++) {
            var child = ctx.getChild(i);
            System.out.println("\tchild: "+child.getText());
        }
    }

    @Override
    public void enterPackageDeclaration(Java9Parser.PackageDeclarationContext ctx) {
        System.out.println("enterPackageDeclaration->"+ctx.getText());
        var packageName = ctx.packageName().getText();
        var packageNode = typeNodes.get(packageName);
        if (packageNode == null) {
            packageNode = new Node(packageName, packageName, NodeType.Package);
            typeNodes.put(packageName, packageNode);
        }
        containerNodeStack.push(packageNode);
    }

    @Override
    public void exitPackageDeclaration(Java9Parser.PackageDeclarationContext ctx) {
        System.out.println("enterPackageDeclaration->"+ctx.getText());
        var packageName = ctx.packageName().getText();
    }

    @Override
    public void enterImportDeclaration(Java9Parser.ImportDeclarationContext ctx) {
        if (ctx.singleTypeImportDeclaration()!=null) {
            var singleImp = ctx.singleTypeImportDeclaration();
            var typeName = singleImp.typeName().getText();
            containerNodeStack.peek().createOutboundEdge(NodeRef.of(typeName), EdgeType.DependsOn);
        }
        //TODO: other 3 types
    }

    @Override
    public void enterClassDeclaration(Java9Parser.ClassDeclarationContext ctx) {
        var decl = ctx.normalClassDeclaration();
        var identTerminal = decl.identifier().Identifier();
        var className = identTerminal.getSymbol().getText();
        Node classNode;
        if (!containerNodeStack.isEmpty()) {
            var containingNode = containerNodeStack.peek();
            var fqn = containingNode.getName() + "." + className;
            classNode = new Node(fqn, fqn, NodeType.Type);
            containingNode.createOutboundEdge(NodeRef.of(classNode), EdgeType.Contains);
        } else {
            classNode = new Node(className, className, NodeType.Type);
        }
        typeNodes.put(classNode.getPath(), classNode);
        containerNodeStack.push(classNode);
    }

    @Override
    public void exitClassDeclaration(Java9Parser.ClassDeclarationContext ctx) {
        containerNodeStack.pop();
    }

    @Override
    public void enterConstructorDeclaration(Java9Parser.ConstructorDeclarationContext ctx) {
        String methodName = ctx.constructorDeclarator().simpleTypeName().getText();
        Node parentNode = containerNodeStack.peek();
        String fqn = parentNode.getName()+"."+methodName;

        Node methodNode = new Node(fqn, fqn, NodeType.Method);
        parentNode.createOutboundEdge(NodeRef.of(methodNode), EdgeType.Contains);
        containerNodeStack.push(methodNode);
    }

    @Override
    public void exitConstructorDeclaration(Java9Parser.ConstructorDeclarationContext ctx) {
        containerNodeStack.pop();
    }

    @Override
    public void enterMethodDeclaration(Java9Parser.MethodDeclarationContext ctx) {
        String methodName = ctx.methodHeader().methodDeclarator().identifier().Identifier().toString();
        Node parentNode = containerNodeStack.peek();
        String fqn = parentNode.getPath()+"."+methodName;

        Node methodNode = new Node(methodName, fqn, NodeType.Method);
        parentNode.createOutboundEdge(NodeRef.of(methodNode), EdgeType.Contains);
        containerNodeStack.push(methodNode);

    }

    @Override
    public void exitMethodDeclaration(Java9Parser.MethodDeclarationContext ctx) {
        containerNodeStack.pop();
    }

    @Override
    public void enterFieldDeclaration(Java9Parser.FieldDeclarationContext ctx) {
        var declarations = ctx.variableDeclaratorList().variableDeclarator();
        var containingNode = containerNodeStack.peek();
        for (var decl : declarations) {
            var id = decl.variableDeclaratorId().identifier().Identifier();
            var child = new Node(id.getSymbol().getText(), buildStandardPath(containingNode, id.getSymbol().getText()), NodeType.Field);
            containingNode.createOutboundEdge(NodeRef.of(child), EdgeType.Contains);

            var typeContext = ctx.unannType();
            var typeNodeRef = getTypeNodeRef(typeContext);
            var fieldNodeRef = NodeRef.of(child);
            fieldNodeRef.getNode().ifPresent(n -> n.createOutboundEdge(typeNodeRef, EdgeType.References));
        }

    }

    @Override
    public void enterLocalVariableDeclaration(Java9Parser.LocalVariableDeclarationContext ctx) {
        NodeRef variableType = getTypeNodeRef(ctx.unannType());
        var containingNode = containerNodeStack.peek();
        var child = new Node("var", buildStandardPath(containingNode, "var"+ctx.toString().hashCode()), NodeType.Variable);
        containingNode.createOutboundEdge(NodeRef.of(child), EdgeType.Declares);
        child.createOutboundEdge(variableType, EdgeType.References);
        addExpressionToStack(child);
        containerNodeStack.push(child);
    }
    @Override public void exitLocalVariableDeclaration( Java9Parser.LocalVariableDeclarationContext ctx) {
        containerNodeStack.pop();
        expressionStack.pop();
    }

    @Override
    public void enterVariableDeclarator(Java9Parser.VariableDeclaratorContext declarator) {
        var containingNode = containerNodeStack.peek();
        var id = declarator.variableDeclaratorId().identifier().Identifier();
        var child = new Node(id.getSymbol().getText(), buildStandardPath(containingNode, id.getSymbol().getText()), NodeType.Variable);
        containingNode.createOutboundEdge(NodeRef.of(child), EdgeType.Declares);
    }


    @Override public void enterAssignment(Java9Parser.AssignmentContext ctx) {
        var containingNode = containerNodeStack.peek();
        String name = ctx.assignmentOperator().getText();
        Node assignmentRoot = new Node(name, buildStandardPath(containingNode, name+ctx.toString().hashCode()), NodeType.Expression);
        addExpressionToStack(assignmentRoot);
    }
    @Override public void exitAssignment(Java9Parser.AssignmentContext ctx) {
        expressionStack.pop();
    }

    public static final String BLOCK_NAME = "<block>";
    @Override
    public void enterBlock(Java9Parser.BlockContext ctx) {
        var parent = containerNodeStack.peek();
        var blockNode = new Node(BLOCK_NAME, buildStandardPath(parent, "<block>"+ctx.toString().hashCode()), NodeType.Block);
        parent.createOutboundEdge(NodeRef.of(blockNode), EdgeType.Contains);
        containerNodeStack.push(blockNode);
        if (!scopeStack.empty()) {
            addStatementNode(blockNode);
        }
        scopeStack.push(new Scope(blockNode, parent.getType() == NodeType.Method));

        if (parent.getType() == NodeType.Method) {
            parent.createOutboundEdge(NodeRef.of(blockNode), EdgeType.Executes);
        }
    }

    @Override
    public void exitBlock(Java9Parser.BlockContext ctx) {
        containerNodeStack.pop();

        var scope = scopeStack.peek();
        var linear = scope.getLinearExecution();
        //likewise the linears within the for loop.
        scope.addCurrentLoopContinuation(linear.getLast());

        exitScope();
    }


    @Override
    public void enterConstructorBody(Java9Parser.ConstructorBodyContext ctx) {
        var parent = containerNodeStack.peek();
        var blockNode = new Node("<block>", parent.getPath()+ctx.toString().hashCode(), NodeType.Block);
        parent.createOutboundEdge(NodeRef.of(blockNode), EdgeType.Contains);
        containerNodeStack.push(blockNode);
        scopeStack.push(new Scope(blockNode, false));
    }

    @Override
    public void exitConstructorBody(Java9Parser.ConstructorBodyContext ctx) {
        containerNodeStack.pop();
        scopeStack.pop();
    }

    private void addStatementNode(Node node) {
        if (scopeStack.empty()) {
            throw new IllegalStateException("pushing statement on emtpy scope stack");
        }
        scopeStack.peek().addLinearExecution(NodeRef.of(node));
    }

    @Override
    public void enterExpressionStatement(Java9Parser.ExpressionStatementContext ctx) {
        var containingNode = containerNodeStack.peek();
        var name = ctx.getText();
        var path = containingNode.getPath()+":"+name.hashCode();
        var node = new Node(name, path, NodeType.Statement);

        addStatementNode(node);
        addExpressionToStack(node);
    }

    @Override
    public void exitExpressionStatement(Java9Parser.ExpressionStatementContext ctx) {
        expressionStack.pop();
    }

    public static String SYNTHETIC_BLOCK_NAME = "syntheticblock";
    public static String FOR_LOOP_NAME = "<for>";
    @Override
    public void enterEnhancedForStatement(Java9Parser.EnhancedForStatementContext ctx) {
        var parent = containerNodeStack.peek();
        var forNode = new Node(FOR_LOOP_NAME , buildStandardPath(parent, "<for>"+ctx.toString().hashCode()), NodeType.Loop);
        containerNodeStack.push(forNode);

        addStatementNode(forNode);

        var blockStatement = ctx.statement().statementWithoutTrailingSubstatement().block();
        if (blockStatement==null) {
            //use the for node as a fake scope
            scopeStack.push(new Scope(forNode, false));
        }


        //NodeRef variableType = getTypeNodeRef(ctx.unannType());
    }
    @Override
    public void exitEnhancedForStatement(Java9Parser.EnhancedForStatementContext ctx) {
        //we pop the for container
        var forNode = containerNodeStack.pop();
        var scope = scopeStack.peek();
        if (scopeStack.peek().getBlockNode().getName().equals(FOR_LOOP_NAME)) {
            var linear = scope.getLinearExecution();
            //we'll need an execute ('return') from the for loop to the next node entered into the block
            scope.addCurrentLoopContinuation(NodeRef.of(forNode));
            //likewise the linears within the for loop.
            scope.addCurrentLoopContinuation(linear.getLast());

            exitScope();
        } else {
            scope.addPendingLoopExit(NodeRef.of(forNode));
        }
        //Note: if there was a real block in the for, it took care of itself
    }

    private Stack<Node> expressionStack = new Stack<>();
    @Override public void enterBasicForStatement(Java9Parser.BasicForStatementContext ctx) {
        var parent = containerNodeStack.peek();
        var forNode = new Node(FOR_LOOP_NAME , buildStandardPath(parent, "<for>"+ctx.toString().hashCode()), NodeType.Loop);
        containerNodeStack.push(forNode);

        addStatementNode(forNode);
        expressionStack.push(forNode);

        var blockStatement = ctx.statement().statementWithoutTrailingSubstatement().block();
        if (blockStatement==null) {
            //use the for node as a fake scope
            scopeStack.push(new Scope(forNode, false));
        }
    }

    @Override public void exitBasicForStatement(Java9Parser.BasicForStatementContext ctx) {
        //we pop the for container
        var forNode = containerNodeStack.pop();
        var scope = scopeStack.peek();
        if (scopeStack.peek().getBlockNode().getName().equals(FOR_LOOP_NAME)) {
            var linear = scope.getLinearExecution();
            //we'll need an execute ('return') from the for loop to the next node entered into the block
            scope.addCurrentLoopContinuation(NodeRef.of(forNode));
            //likewise the linears within the for loop.
            scope.addCurrentLoopContinuation(linear.getLast());

            exitScope();
        } else {
            scope.addPendingLoopExit(NodeRef.of(forNode));
        }
        expressionStack.pop();
    }

    @Override public void enterConditionalExpression(Java9Parser.ConditionalExpressionContext ctx) {
        var q = ctx.QUESTION();
        if (q!=null) {
            //TODO: trinary operator
        }
    }

    @Override public void enterConditionalOrExpression(Java9Parser.ConditionalOrExpressionContext ctx) {
        var or = ctx.OR();
        if (or != null) {
            //TODO: logical or expression
        }
    }

    @Override public void enterConditionalAndExpression(Java9Parser.ConditionalAndExpressionContext ctx) {
        var and = ctx.AND();
        if (and != null) {
            //TODO: logical and expression
        }
    }

    @Override public void enterEqualityExpression(Java9Parser.EqualityExpressionContext ctx) {
        if (ctx.EQUAL()!=null || ctx.NOTEQUAL()!=null) { //How's that for meta?
            String name = (ctx.EQUAL() != null ? ctx.EQUAL().getSymbol() : ctx.NOTEQUAL().getSymbol()).getText();
            var parent = containerNodeStack.peek();
            Node equalityNode = new Node(name, buildStandardPath(parent, "<for>"+ctx.toString().hashCode()), NodeType.Expression);
        }
    }

    @Override public void enterRelationalExpression(Java9Parser.RelationalExpressionContext ctx) {
        String operator = getRelationalOperator(ctx);
        if (operator != null) {
            var parent = containerNodeStack.peek();
            Node relationalNode = new Node(operator, buildStandardPath(parent, operator+ctx.toString().hashCode()), NodeType.Expression);
            addExpressionToStack(relationalNode);
        }
    }

    private void addExpressionToStack(Node expressionNode) {
        if (!expressionStack.empty() && expressionStack.peek()!=null) {
            var fork = expressionStack.peek();
            fork.createOutboundEdge(NodeRef.of(expressionNode), EdgeType.Evaluates);
        }
        expressionStack.push(expressionNode);
    }

    @Override public void exitRelationalExpression(Java9Parser.RelationalExpressionContext ctx) {
        String operator = getRelationalOperator(ctx);
        if (operator != null) {
            expressionStack.pop();
        }
    }

    @Override public void enterExpressionName(Java9Parser.ExpressionNameContext ctx) {
        if (ctx.identifier()!=null) {
            String name = ctx.identifier().getText();
            var parent = containerNodeStack.peek();
            Node relationalNode = new Node(name, buildStandardPath(parent, name+ctx.toString().hashCode()), NodeType.Expression);
            addExpressionToStack(relationalNode);
        }
        if (ctx.ambiguousName()!=null) {
            String name = ctx.ambiguousName().getText();
            var parent = containerNodeStack.peek();
            Node relationalNode = new Node(name, buildStandardPath(parent, name+ctx.toString().hashCode()), NodeType.Expression);
            addExpressionToStack(relationalNode);
        }
    }

    @Override public void exitExpressionName(Java9Parser.ExpressionNameContext ctx) {
        if (ctx.identifier()!=null || ctx.ambiguousName()!=null) {
            expressionStack.pop();
        }
    }

    @Override public void enterMethodInvocation_lf_primary(Java9Parser.MethodInvocation_lf_primaryContext ctx) {
        String name = ctx.identifier().getText();
        var parent = containerNodeStack.peek();
        Node relationalNode = new Node(name, buildStandardPath(parent, "<for>"+ctx.toString().hashCode()), NodeType.Expression);
        addExpressionToStack(relationalNode);
    }

    @Override public void exitMethodInvocation_lf_primary(Java9Parser.MethodInvocation_lf_primaryContext ctx) {
        expressionStack.pop();
    }

    @Override public void enterMethodInvocation_lfno_primary(Java9Parser.MethodInvocation_lfno_primaryContext ctx) {
        String objectName = ctx.typeName().getText();
        String name = ctx.identifier().getText();
        var parent = containerNodeStack.peek();
        Node relationalNode = new Node(ctx.getText(), buildStandardPath(parent, "<for>"+ctx.toString().hashCode()), NodeType.Expression);
        addExpressionToStack(relationalNode);
    }

    @Override public void exitMethodInvocation_lfno_primary(Java9Parser.MethodInvocation_lfno_primaryContext ctx) {
        expressionStack.pop();
    }

    @Override
    public void enterPostfixExpression(Java9Parser.PostfixExpressionContext ctx) {
        var thing1 = ctx.postDecrementExpression_lf_postfixExpression();
        var thing2 = ctx.postIncrementExpression_lf_postfixExpression();
        var name = ctx.expressionName();
        var primatey = ctx.primary();
        String operator = ctx.getText();
    }
    @Override public void exitPostfixExpression(Java9Parser.PostfixExpressionContext ctx) {

    }

    @Override public void enterPostIncrementExpression(Java9Parser.PostIncrementExpressionContext ctx) {
        var operator = ctx.INC().getText();
        var parent = containerNodeStack.peek();
        Node operatorNode = new Node(operator, buildStandardPath(parent, operator+ctx.toString().hashCode()), NodeType.Expression);
        addExpressionToStack(operatorNode);
    }

    @Override public void exitPostIncrementExpression(Java9Parser.PostIncrementExpressionContext ctx) {
        expressionStack.pop();
    }

    private static String getRelationalOperator(Java9Parser.RelationalExpressionContext ctx) {
        if (ctx.GT() != null) {
            return ctx.GT().getSymbol().getText();
        }
        if (ctx.LT() != null) {
            return ctx.LT().getSymbol().getText();
        }
        if (ctx.GE() != null) {
            return ctx.GE().getSymbol().getText();
        }
        if (ctx.LE() != null) {
            return ctx.LE().getSymbol().getText();
        }
        if (ctx.INSTANCEOF() != null) {
            return ctx.INSTANCEOF().getSymbol().getText();
        }
        return null;
    }

    private NodeRef getTypeNodeRef(Java9Parser.UnannTypeContext typeContext) {
        var refType = typeContext.unannReferenceType();
        if (refType != null) {
            var type = refType.unannClassOrInterfaceType();
            if (type == null && refType.unannArrayType()!=null) {
                var array = refType.unannArrayType();
                type = array.unannClassOrInterfaceType() ;
            }
            if (type != null) {
                var typeName = type.getText();
                return NodeRef.of(typeName);
            }
        }
        var primitiveType = typeContext.unannPrimitiveType();
        if (primitiveType != null) {
            var name = primitiveType.getText();
            return NodeRef.of(name);
        }
        return null;
    }

    //precondition: scope's block node has been popped from containerStack
    private void exitScope() {
        var scope = scopeStack.pop();
        var block = scope.getBlockNode();
        var linear = scope.getLinearExecution();
        //linears are already checked, just need to tie them in
        block.createOutboundEdge(linear.getFirst(), EdgeType.Executes);
        //pass current continuations to pending
        if (scope.isMethod()) {
            var methodNode = containerNodeStack.peek();
            if (methodNode.getType()!=NodeType.Method) {
                throw new IllegalStateException("Block marked method has non-method parent, or precondition of exitScope not met");
            }

            //TODO: this really should only happen for void methods, add check
            for (var exit : scope.getPendingLoopExits()) {
                exit.getNode().get().createOutboundEdge(NodeRef.of(methodNode), EdgeType.Executes);
            }
        } else {
            var containingScope = scopeStack.peek();
            for (var exit : scope.getCurrentLoopContinuations()) {
                containingScope.addPendingLoopExit(exit);
            }
        }
    }

    private static String buildStandardPath(Node parent, String childName) {
        return parent.getPath()+":"+childName;
    }
}
