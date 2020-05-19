package com.nvisia.sourcegraph.graph;

import com.nvisia.sourcegraph.AntlrImport;
import org.antlr.v4.runtime.CharStreams;
import static org.junit.Assert.*;
import org.junit.Test;

import java.util.Optional;

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
    public void testForLoop() {
        var imp = importString(ENHANCED_FOR_LOOP);
        System.out.println(imp.toDOT());
        var topLevel = imp.getTopLevelNodes();
        assertEquals(1, topLevel.size());
        var topNode = topLevel.iterator().next();
        assertEquals(NodeType.Package, topNode.getType());

//        var method = getMethodNode(topNode, "featureTest");
//        assertNotNull(method);



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
