package com.nvisia.sourcegraph.graph;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Node implements Comparable<Node> {
    private String name;
    private String path;
    private NodeType type;
    private List<Edge> outboundEdges = new ArrayList<>();

    public Node(String name, String path, NodeType type) {
        this.name = name;
        this.path = path;
        this.type = type;
    }
    public Node(String name, String path, NodeType type, List<Edge> outboundEdges) {
        this(name, path, type);
        this.outboundEdges = outboundEdges;
    }
    public String toString() {
        return type.toString()+":"+name;
    }

    public void preOrderTraverse(BiConsumer<Optional<EdgeType>, NodeRef> visitor) {
        visitor.accept(Optional.empty(), NodeRef.of(this));
        var nodeSet = new HashSet<NodeRef>();
        for (var edge : outboundEdges) {
            doPreOrderTraverse(Optional.of(edge), visitor, nodeSet);
        }
    }
    private void doPreOrderTraverse(Optional<Edge> inEdge, BiConsumer<Optional<EdgeType>, NodeRef> visitor, Set<NodeRef> alreadyVisited) {
        inEdge.ifPresent(edge -> {
            if (alreadyVisited.contains(edge.getTo())) {
                return;
            }
            visitor.accept(Optional.of(edge.getType()), edge.getTo());
            alreadyVisited.add(edge.getTo());
            for (var childEdge : outboundEdges) {
                childEdge.getTo().getNode().ifPresent(node -> node.doPreOrderTraverse(Optional.of(edge), visitor, alreadyVisited));
            }
        });
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public NodeType getType() {
        return type;
    }

    public List<Edge> getOutboundEdges() {
        return Collections.unmodifiableList(outboundEdges);
    }
    public void addOutboundEdge(Edge e) {
        outboundEdges.add(e);
    }

    @Override
    public int compareTo(Node o) {
        return this.toString().compareTo(o.toString());
    }
}
