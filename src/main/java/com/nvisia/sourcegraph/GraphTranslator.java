package com.nvisia.sourcegraph;

import com.nvisia.sourcegraph.antlr.Java9Parser;
import com.nvisia.sourcegraph.graph.Edge;
import com.nvisia.sourcegraph.graph.EdgeType;
import com.nvisia.sourcegraph.graph.Node;
import com.nvisia.sourcegraph.graph.NodeRef;
import com.nvisia.sourcegraph.graph.NodeType;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.*;

public class GraphTranslator extends com.nvisia.sourcegraph.antlr.Java9BaseListener {
    private Map<String, Node> typeNodes = new TreeMap<>();
    private Stack<Node> containerNodeStack = new Stack<>();

    public GraphTranslator() {
    }

    public List<Node> getTopLevelNodes() {
        return Collections.singletonList(containerNodeStack.peek());
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
        var containingNode = containerNodeStack.peek();
        var fqn = containingNode.getName()+"."+className;
        var classNode = new Node(fqn, fqn, NodeType.Type);
        typeNodes.put(fqn, classNode);
        containingNode.createOutboundEdge(NodeRef.of(classNode), EdgeType.Contains);
        containerNodeStack.push(classNode);
    }

    @Override
    public void exitClassDeclaration(Java9Parser.ClassDeclarationContext ctx) {
        containerNodeStack.pop();
    }

    @Override
    public void enterMethodDeclaration(Java9Parser.MethodDeclarationContext ctx) {
        String name = ctx.methodHeader().methodDeclarator().identifier().Identifier().toString();
        Node parentNode = containerNodeStack.peek();
        String fqn = parentNode.getName()+"."+name;
        Node myNode = new Node(fqn, fqn, NodeType.Method);
        parentNode.createOutboundEdge(NodeRef.of(myNode), EdgeType.Contains);
        containerNodeStack.push(myNode);
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
            var child = new Node(id.getSymbol().getText(), id.getSymbol().getText(), NodeType.Field);
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
            var child = new Node(id.getSymbol().getText(), id.getSymbol().getText(), NodeType.Variable);
            containingNode.createOutboundEdge(NodeRef.of(child), EdgeType.Contains);

            var fieldNodeRef = NodeRef.of(child);
            child.createOutboundEdge(variableType, EdgeType.References);
        }
    }

    @Override
    public void enterBlock(Java9Parser.BlockContext ctx) {
        var parent = containerNodeStack.peek();
        var blockNode = new Node("<block>", "<block>", NodeType.Block);
        parent.createOutboundEdge(NodeRef.of(blockNode), EdgeType.Contains);
        containerNodeStack.push(blockNode);
    }

    @Override
    public void exitBlock(Java9Parser.BlockContext ctx) {
        containerNodeStack.pop();
    }

    private NodeRef getTypeNodeRef(Java9Parser.UnannTypeContext typeContext) {
        var refType = typeContext.unannReferenceType();
        if (refType != null) {
            var type = refType.unannClassOrInterfaceType();
            if (type != null) {
                var typeName = type.getText();
                return NodeRef.of(typeName);
            }
        }
        return null;
    }
}
