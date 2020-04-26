package com.nvisia.sourcegraph.graph;

import java.util.Optional;

@FunctionalInterface
public interface NodeVisitor {
    void visitEdge(Optional<EdgeType> edgeType, NodeRef nodeRef, int level);
}
