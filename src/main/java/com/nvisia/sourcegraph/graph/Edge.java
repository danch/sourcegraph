package com.nvisia.sourcegraph.graph;

import java.util.Map;
import java.util.Objects;

public class Edge {
    private NodeRef from;
    private NodeRef to;
    private EdgeType type;

    public Edge(NodeRef from, NodeRef to, EdgeType type) {
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
        if (!from.isResolved()) {
            var actualNode = typeCache.get(from.getNodePath());
            from.resolveWith(actualNode);
        }
        if (!to.isResolved()) {
            var actualNode = typeCache.get(to.getNodePath());
            to.resolveWith(actualNode);
        }
    }
}
