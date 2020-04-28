package com.nvisia.sourcegraph.graph;

import java.util.Objects;
import java.util.Optional;

public class NodeRef {
    private String nodePath;
    private Optional<Node> node = Optional.empty();

    /* syntax saccharin */
    public static NodeRef of(Node node) {
        return new NodeRef(node, node.getPath());
    }
    public static NodeRef of(String path) {
        return new NodeRef(path);
    }

    public String toString() {
        return node.map(Node::toString).orElse(nodePath);
    }

    private NodeRef(Node node, String nodePath) {
        this.node = Optional.of(node);
        this.nodePath = nodePath;
    }

    private NodeRef(String nodePath) {
        this.nodePath = nodePath;
    }

    public boolean isResolved() {
        return node.isPresent();
    }
    public void resolveWith(Node node) {
        if (node != null && (this.node.isEmpty() || !this.node.equals(node))) {
            this.node = Optional.of(node);
        }
    }

    public String getNodePath() {
        return nodePath;
    }

    public Optional<Node> getNode() {
        return node;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeRef nodeRef = (NodeRef) o;
        return Objects.equals(nodePath, nodeRef.nodePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodePath);
    }
}
