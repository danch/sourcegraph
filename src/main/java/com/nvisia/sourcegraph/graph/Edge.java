package com.nvisia.sourcegraph.graph;

import java.util.Map;
import java.util.Optional;

public class Edge {
    private NodeRef from;
    private NodeRef to;
    private EdgeType type;

    public Edge(NodeRef from, NodeRef to, EdgeType type) {
        if (!from.isResolved()) {
            throw new IllegalStateException("'from' must be resolved");
        }
        this.from = from;
        this.to = to;
        this.type = type;
    }

    public NodeRef getFrom() {
        return from;
    }

    public NodeRef getTo() {
        return to;
    }

    public EdgeType getType() {
        return type;
    }

    public void resolveNodeRefs(Map<String, Node> typeCache) {
        if (!to.isResolved() && from.isResolved()) {
            var actualNode = typeCache.get(to.getNodePath());
            if (actualNode == null && !to.getNodePath().contains(".")) {
                //not fully qualified, look in current package
                Node packageNode = findNearestPackage(from.getNode().get());
                var packageName = packageNode.getName();
                var fqn = packageName + "." + to.getNodePath();
                actualNode = typeCache.get(fqn);
                //TODO imports
            }
            to.resolveWith(actualNode);
        }
    }

    private Node findNearestPackage(Node actualNode) {
        for (var e: actualNode.getInboundEdges()) {
            if (e.getType()==EdgeType.Contains) {
                if (!e.getFrom().isResolved()) {
                    throw new IllegalStateException("Containing node not resolved");
                }
                if (e.getFrom().getNode().map(n -> n.getType()).orElse(NodeType.Unknown) == NodeType.Package) {
                    return e.getFrom().getNode().get();
                } else {
                    return findNearestPackage(e.getFrom().getNode().get());
                }
            }
        }
        return null;//actually an invalid state
    }
}
