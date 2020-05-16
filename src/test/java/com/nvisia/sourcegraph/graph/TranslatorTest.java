package com.nvisia.sourcegraph.graph;

import com.nvisia.sourcegraph.AntlrImport;
import org.antlr.v4.runtime.CharStreams;
import static org.junit.Assert.*;
import org.junit.Test;

public class TranslatorTest {
    private final String ENHANCED_FOR_LOOP =
            "class T {" +
            "public static void featureTest() {" +
                "List<String> list;" +
                "for (String s: list) " +
                "    System.out.println(s);" +
            "}"+
            "}";
    @Test
    public void testForLoop() {
        var imp = importString(ENHANCED_FOR_LOOP);
        imp.textDumpContainsGraph();
        var topLevel = imp.getTopLevelNodes();
        assertEquals(1, topLevel.size());
        var topNode = topLevel.iterator().next();
        assertEquals(NodeType.Type, topNode.getType());

        var method = getMethodNode(topNode, "featureTest");
        assertNotNull(method);



    }

    private Node getMethodNode(Node type, String methodName) {
        for (var edge : type.getOutboundEdges()) {
            if (edge.getTo().isResolved()) {
                var node = edge.getTo().getNode().get();
                if (node.getType().equals(NodeType.Method)) {
                    if (node.getName().equals(methodName)) {
                        return node;
                    }
                }
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
