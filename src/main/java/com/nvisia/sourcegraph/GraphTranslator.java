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
        for (var declarator : ctx.variableDeclaratorList().variableDeclarator()) {
            var id = declarator.variableDeclaratorId().identifier().Identifier();
            var child = new Node(id.getSymbol().getText(), buildStandardPath(containingNode, id.getSymbol().getText()), NodeType.Variable);
            containingNode.createOutboundEdge(NodeRef.of(child), EdgeType.Contains);

            child.createOutboundEdge(variableType, EdgeType.References);

            var initializer = declarator.variableInitializer();
            if (initializer!=null) {
                //if there's an initializer, the declaration is also treated as a statement
                addStatementNode(child);
            }
        }
    }

    @Override
    public void exitLocalVariableDeclaration( Java9Parser.LocalVariableDeclarationContext ctx) {
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
    }

    @Override
    public void exitExpressionStatement(Java9Parser.ExpressionStatementContext ctx) {
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
