package com.nvisia.sourcegraph.graph;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import java.util.HashMap;
import java.util.Map;

public class NodeTest {

    @Test
    public void preOrderTraverseTest() {
        Node a = new Node("a", "a", NodeType.Package);
        Node b = new Node("b", "b", NodeType.Type);
        Node c = new Node("c", "c", NodeType.Type);
        Edge e = new Edge(NodeRef.of(a), NodeRef.of(b), EdgeType.Owns);
        Edge f = new Edge(NodeRef.of(a), NodeRef.of(c), EdgeType.Owns);
        a.addOutboundEdge(e);
        a.addOutboundEdge(f);

        Map<String, Integer> visitCounts = new HashMap<>();
        visitCounts.put("a", 0);
        visitCounts.put("b", 0);
        visitCounts.put("c", 0);
        a.preOrderTraverse((maybeEdgeType, nodeRef) -> {
            String name = nodeRef.getNode().map(Node::getName).orElse("");
            Integer oldValue = visitCounts.get(name);
            Integer newValue = oldValue+1;
            visitCounts.put(name, newValue);
        });

        assertThat("More than two nodes visited", visitCounts.size(), is(3));
        for (Integer i : visitCounts.values()) {
            if (i != 1) {
                fail("node visited more than once");
            }
        }
    }

    @Test
    public void compareToTest() {
    }
}
