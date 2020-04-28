package com.nvisia.sourcegraph.graph;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Node implements Comparable<Node> {
    private String name;
    private String path;
    private NodeType type;
    private List<Edge> outboundEdges = new ArrayList<>();
    private List<Edge> inboundEdges = new ArrayList<>();

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

    public void preOrderTraverse(Optional<EdgeType> typeToTraverse, NodeVisitor visitor) {
        var nodeSet = new HashSet<NodeRef>();
        Node fakeNod = new Node("__root__", "__root__", NodeType.Package);
        doPreOrderTraverse(typeToTraverse, Optional.of(new Edge(NodeRef.of(fakeNod), NodeRef.of(this), EdgeType.Contains)), visitor, nodeSet, 0);
    }
    private void doPreOrderTraverse(Optional<EdgeType> typeToTraverse, Optional<Edge> inEdge, NodeVisitor visitor, Set<NodeRef> alreadyVisited, int level) {
        inEdge.ifPresent(edge -> {
            if (alreadyVisited.contains(edge.getTo())) {
                return;
            }
            visitor.visitEdge(Optional.of(edge.getType()), edge.getTo(), level);
            alreadyVisited.add(edge.getTo());
            for (var childEdge : outboundEdges) {
                //Hack/workaround for the fact that the post-processing resolution of types (from local to fully qualified) leaves
                //  edges to nowhere in the case of types outside the system (like java.lang.*)
                if (typeToTraverse.map(e -> e.equals(childEdge.getType()) ).orElse(true) ) {
                    childEdge.getTo().getNode().ifPresentOrElse(
                            node -> node.doPreOrderTraverse(typeToTraverse, Optional.of(childEdge), visitor, alreadyVisited, level + 1),
                            () -> visitor.visitEdge(Optional.of(childEdge.getType()), childEdge.getTo(), level + 1) );
                } else {
                    visitor.visitEdge(Optional.of(childEdge.getType()), childEdge.getTo(), level + 1);
                }
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
    void addOutboundEdge(Edge e) {
        if (!e.getFrom().equals(NodeRef.of(this))) {
            throw new IllegalStateException("add of outbound edge to wrong node");
        }
        outboundEdges.add(e);
    }
    public Edge createOutboundEdge(NodeRef to, EdgeType type) {
        var edge = new Edge(NodeRef.of(this), to, type);
        addOutboundEdge(edge);
        to.getNode().ifPresent(n -> n.addInboundEdge(edge));
        return edge;
    }

    public List<Edge> getInboundEdges() {
        return Collections.unmodifiableList(inboundEdges);
    }
    void addInboundEdge(Edge e) {
        if (!e.getTo().equals(NodeRef.of(this))) {
            throw new IllegalStateException("add of outbound edge to wrong node");
        }
        inboundEdges.add(e);
    }

    @Override
    public int compareTo(Node o) {
        return this.toString().compareTo(o.toString());
    }
}
