package com.nvisia.sourcegraph.graph;

import com.nvisia.sourcegraph.Scope;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class ScopeTest {
    @Test
    public void testAddLinearExecution() {
        Node fakeBlock = new Node("{}", "{}", NodeType.Block);
        Scope scope = new Scope(fakeBlock, false);
        scope.addLinearExecution(generateArbitraryNodeRef());
        scope.addLinearExecution(generateArbitraryNodeRef());
        scope.addLinearExecution(generateArbitraryNodeRef());

        assertThat(fakeBlock.getOutboundEdges().size(), is(3));
    }

    int counter = -1;
    private NodeRef generateArbitraryNodeRef() {
        counter++;//not thread safe (not that it actually matters)
        return NodeRef.of(new Node("A"+counter, "B"+counter, NodeType.Statement));
    }
}
