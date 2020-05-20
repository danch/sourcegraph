package com.nvisia.sourcegraph;

import com.nvisia.sourcegraph.graph.Node;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

public class ContainerNodeStack {
    private Stack<Node> stack = new Stack<>();
    private Set<Node> roots = new HashSet<>();

    public boolean isEmpty() {
        return stack.isEmpty();
    }
    public void push(Node node) {
        if (stack.isEmpty()) {
            roots.add(node);
        }
        stack.push(node);
    }
    public Node peek() {
        return stack.peek();
    }
    public Node pop() {
        return stack.pop();
    }

    public Collection<Node> getRootNodes() {
        return roots;
    }
}
