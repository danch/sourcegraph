package com.nvisia.sourcegraph;

import com.nvisia.sourcegraph.graph.EdgeType;
import com.nvisia.sourcegraph.graph.Node;
import com.nvisia.sourcegraph.graph.NodeRef;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Scope {
    private boolean isMethod;
    private Node blockNode;
    private LinkedList<NodeRef> linearExecution = new LinkedList<>();
    private List<NodeRef> forwardReturns = new ArrayList<>();//noderefs that need to be tied to the exit of the scope as an execute (return paths)

    public Scope(Node blockNode, boolean isMethod) {
        this.isMethod = isMethod;
        this.blockNode = blockNode;
    }

    public Node getBlockNode() {
        return blockNode;
    }

    public void addForwardReturn(NodeRef returnFrom) {
        forwardReturns.add(returnFrom);
    }
    public List<NodeRef> getForwardReturns() {
        return forwardReturns;
    }

    public void addLinearExecution(NodeRef executed) {
        if (!linearExecution.isEmpty()) {
            var lastNode = linearExecution.getLast();
            lastNode.getNode().ifPresentOrElse(n -> n.createOutboundEdge(executed, EdgeType.Executes),
                    ()->System.err.println("Error building executin graph: node not bound for "+lastNode.getNodePath()));
        }
        blockNode.createOutboundEdge(executed, EdgeType.Contains);
        linearExecution.addLast(executed);

        //check for pending returns from conditional/looping blocks
        for (var nodeRef : forwardReturns) {
            nodeRef.getNode().ifPresentOrElse(node -> node.createOutboundEdge(executed, EdgeType.Executes),
                    () -> {throw new IllegalStateException("resolved for node is required to fixup dangling loops/conditionals");});
            forwardReturns = new ArrayList<>();
        }
    }

    public LinkedList<NodeRef> getLinearExecution() {
        return linearExecution;
    }
}
