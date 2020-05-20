package com.nvisia.sourcegraph.graph;

import com.nvisia.sourcegraph.AntlrImport;
import org.antlr.v4.runtime.CharStreams;
import static org.junit.Assert.*;
import org.junit.Test;

import java.util.Optional;
import java.util.stream.Collectors;

public class TranslatorTest {
    private final String ENHANCED_FOR_LOOP =
            "package graphtest;" +
            "class ForLoopingType {" +
            "public static void featureTest() {" +
                "List<String> dummyList;" +
                "for (String s: list) " +
                "    System.out.println(s);" +
            "}"+
            "}";
    @Test
    public void testEnhancedForLoop() {
        var imp = importString(ENHANCED_FOR_LOOP);
        System.out.println(imp.toDOT());
        var topLevel = imp.getTopLevelNodes();
        assertEquals(1, topLevel.size());
        var topNode = topLevel.iterator().next();
        assertEquals(NodeType.Package, topNode.getType());

        Node typeNode = null;
        for (var edge : topNode.findOutboundEdgesOfType(EdgeType.Contains)) {
            if (edge.getTo().getNode().get().getType() == NodeType.Type) {
                typeNode = edge.getTo().getNode().get();
                break;
            }
        }
        var method = getMethodNode(typeNode, "featureTest");
        assertNotNull(method);

        var executions = method.findOutboundEdgesOfType(EdgeType.Executes);
        assertEquals(1, executions.size());
        var blockEdge = executions.iterator().next();
        var blockNode = blockEdge.getTo().getNode().get();
        assertTrue(blockNode.getType()==NodeType.Block);

        var edgesToFor = blockNode.findOutboundEdgesToNodeType(NodeType.Loop);
        assertEquals(2, edgesToFor.size());

        var forLoop = edgesToFor.stream().filter(e -> blockEdge.getType() == EdgeType.Executes).findFirst().get().getTo().getNode().get();
        var forExecutions = forLoop.findOutboundEdgesOfType(EdgeType.Executes);
        assertEquals(2, forExecutions.size());
        assertEquals( 1, forExecutions.stream().filter(e -> e.getTo().getNode().get().getType() == NodeType.Method).count() );

        var statementEdges = forExecutions.stream().filter(e -> e.getTo().getNode().get().getType() == NodeType.Statement).collect(Collectors.toList());
        assertEquals( 1, statementEdges.size() );
        var statement = statementEdges.iterator().next().getTo().getNode().get();
        var statementExecutions = statement.findOutboundEdgesOfType(EdgeType.Executes);
        assertEquals(1, statementExecutions.size());
    }

    private Node getMethodNode(Node type, String methodName) {
        var methods = type.findOutboundEdgesToNodeType(NodeType.Method);
        for (var methodEdge : methods) {
            String actualName = methodEdge.getTo().getNode().map(node -> node.getName()).get();

            if (actualName.equals(methodName)) {
                return methodEdge.getTo().getNode().get();
            }
        }
        return null;
    }

    private static AntlrImport importString(String text) {
        AntlrImport imp = new AntlrImport();
        var inStream  = CharStreams.fromString(text);
        imp.importSource(inStream);
        imp.postProcess();
        return imp;
    }
}
