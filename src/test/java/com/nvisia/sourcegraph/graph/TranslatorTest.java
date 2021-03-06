package com.nvisia.sourcegraph.graph;

import com.nvisia.sourcegraph.AntlrImport;
import com.nvisia.sourcegraph.GraphTranslator;
import org.antlr.v4.runtime.CharStreams;

import static com.nvisia.sourcegraph.GraphTranslator.BLOCK_NAME;
import static com.nvisia.sourcegraph.GraphTranslator.FOR_LOOP_NAME;
import static org.junit.Assert.*;
import org.junit.Test;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TranslatorTest {

    private static Set<EdgeType> edgeTypesToExcludeFromGraphVis = new HashSet<>();
    static {
        edgeTypesToExcludeFromGraphVis.add(EdgeType.Contains);
    }

    private final String ENHANCED_FOR_LOOP =
            "package graphtest;" +
            "class ForLoopingType {" +
            "public static void featureTest() {" +
                "List<String> dummyList;" +
                "for (String s: list) " +
                "    System.out.println(s);" +
            "}"+
            "}";
    @Test
    public void testEnhancedForLoop() {
        var imp = importString(ENHANCED_FOR_LOOP);
        System.out.println(imp.toDOT(edgeTypesToExcludeFromGraphVis));
        var topLevel = imp.getTopLevelNodes();
        assertEquals(1, topLevel.size());
        var topNode = topLevel.iterator().next();
        assertEquals(NodeType.Package, topNode.getType());

        Node typeNode = null;
        for (var edge : topNode.findOutboundEdgesOfType(EdgeType.Contains)) {
            if (edge.getTo().getNode().get().getType() == NodeType.Type) {
                typeNode = edge.getTo().getNode().get();
                break;
            }
        }
        var method = getMethodNode(typeNode, "featureTest");
        assertNotNull(method);

        var executions = method.findOutboundEdgesOfType(EdgeType.Executes);
        assertEquals(1, executions.size());
        var blockEdge = executions.iterator().next();
        var blockNode = blockEdge.getTo().getNode().get();
        assertTrue(blockNode.getType()==NodeType.Block);

        var edgesToFor = blockNode.findOutboundEdgesToNodeType(NodeType.Loop);
        assertEquals(2, edgesToFor.size());

        var forLoop = edgesToFor.stream().filter(e -> blockEdge.getType() == EdgeType.Executes).findFirst().get().getTo().getNode().get();
        var forExecutions = forLoop.findOutboundEdgesOfType(EdgeType.Executes);
        assertEquals(2, forExecutions.size());
        assertEquals( 1, forExecutions.stream().filter(e -> e.getTo().getNode().get().getType() == NodeType.Method).count() );

        var statementEdges = forExecutions.stream().filter(e -> e.getTo().getNode().get().getType() == NodeType.Statement).collect(Collectors.toList());
        assertEquals( 1, statementEdges.size() );
        var statement = statementEdges.iterator().next().getTo().getNode().get();
        var statementExecutions = statement.findOutboundEdgesOfType(EdgeType.Executes);
        assertEquals(1, statementExecutions.size());
    }

    private final String ENHANCED_FOR_LOOP_WITH_BLOCK =
            "package graphtest;" +
                    "class ForLoopingType {" +
                    "    public static void featureTest() {" +
                    "        List<String> dummyList;" +
                    "        for (String s: list) {" +
                    "            System.out.println(s);" +
                    "        }"+
                    "    }" +
                    "}";


    @Test
    public void testEnhancedForLoopWithBlock() {
        var imp = importString(ENHANCED_FOR_LOOP_WITH_BLOCK);
        System.out.println(imp.toDOT(edgeTypesToExcludeFromGraphVis));
        var topLevel = imp.getTopLevelNodes();
        var topNode = topLevel.iterator().next();

        var forEdge = findFirstMatch(topNode, edgeAndnodeTypesAndNameFilter(EdgeType.Executes, NodeType.Loop, FOR_LOOP_NAME));
        assertNotNull(forEdge);
        var forEdgeByContains = findFirstMatch(topNode, edgeAndnodeTypesAndNameFilter(EdgeType.Contains, NodeType.Loop, FOR_LOOP_NAME));
        assertNotNull(forEdgeByContains);

        var forNode = forEdge.getTo().getNode().get();

        var forContainsBlock = findFirstMatch(forNode, edgeAndnodeTypesAndNameFilter(EdgeType.Contains, NodeType.Block, BLOCK_NAME));
        assertNotNull(forContainsBlock);
        var forExecutesBlock = findFirstMatch(forNode, edgeAndnodeTypesAndNameFilter(EdgeType.Contains, NodeType.Block, BLOCK_NAME));
        assertNotNull(forExecutesBlock);

        var blockNode = forExecutesBlock.getTo().getNode().get();
        var blockExecutesStatement = assertContainsAndExecutes(blockNode, NodeType.Statement, "System.out.println(s);");
        var statementExecutesMethod = findFirstMatch(blockExecutesStatement.getTo().getNode().get(),
                edgeAndnodeTypesAndNameFilter(EdgeType.Executes, NodeType.Method, "featureTest"));
        assertNotNull(statementExecutesMethod);
        var forExecutesMethod = findFirstMatch(forNode,
                edgeAndnodeTypesAndNameFilter(EdgeType.Executes, NodeType.Method, "featureTest"));
        assertNotNull(forExecutesMethod);
    }

    private final String FOR_LOOP_WITH_BLOCK =
            "package graphtest;" +
                    "class ForLoopingType {" +
                    "    public static void featureTest() {" +
                    "        List<String> dummyList;" +
                    "        for (int i=0;i<dummyList.length();i++) {" +
                    "            System.out.println(dummyList.get(i));" +
                    "        }"+
                    "    }" +
                    "}";

    @Test
    public void testForLoopWithBlock() {
        var imp = importString(FOR_LOOP_WITH_BLOCK);
        System.out.println(imp.toDOT(edgeTypesToExcludeFromGraphVis));
        var topLevel = imp.getTopLevelNodes();
        var topNode = topLevel.iterator().next();

        var forEdge = findFirstMatch(topNode, edgeAndnodeTypesAndNameFilter(EdgeType.Executes, NodeType.Loop, FOR_LOOP_NAME));
        assertNotNull(forEdge);
        var forNode = forEdge.getTo().getNode().get();
        var declaration = findFirstMatch(forNode, edgeAndNodeTypesFilter(EdgeType.Declares, NodeType.Variable));
        assertNotNull(declaration);

        var evaluations = matchingEdgesFrom(forNode,edgeAndNodeTypesFilter(EdgeType.Evaluates, NodeType.Expression));
        assertEquals(2, evaluations.size());

        var forContainsBlock = findFirstMatch(forNode, edgeAndnodeTypesAndNameFilter(EdgeType.Contains, NodeType.Block, BLOCK_NAME));
        assertNotNull(forContainsBlock);
        var forExecutesBlock = findFirstMatch(forNode, edgeAndnodeTypesAndNameFilter(EdgeType.Contains, NodeType.Block, BLOCK_NAME));
        assertNotNull(forExecutesBlock);

        var blockNode = forExecutesBlock.getTo().getNode().get();
        var blockExecutesStatement = assertContainsAndExecutes(blockNode, NodeType.Statement, "System.out.println(dummyList.get(i));");
        var statementExecutesMethod = findFirstMatch(blockExecutesStatement.getTo().getNode().get(),
                edgeAndnodeTypesAndNameFilter(EdgeType.Executes, NodeType.Method, "featureTest"));
        assertNotNull(statementExecutesMethod);
        var forExecutesMethod = findFirstMatch(forNode,
                edgeAndnodeTypesAndNameFilter(EdgeType.Executes, NodeType.Method, "featureTest"));
        assertNotNull(forExecutesMethod);

    }


    private Edge assertContainsAndExecutes(Node parent, NodeType nodeType, String name) {
        var contains = findFirstMatch(parent, edgeAndnodeTypesAndNameFilter(EdgeType.Contains, nodeType, name));
        assertNotNull(contains);
        var executes = findFirstMatch(parent, edgeAndnodeTypesAndNameFilter(EdgeType.Executes, nodeType, name));
        assertNotNull(executes);
        return executes;
    }

    private Node getMethodNode(Node type, String methodName) {
        var methods = type.findOutboundEdgesToNodeType(NodeType.Method);
        for (var methodEdge : methods) {
            String actualName = methodEdge.getTo().getNode().map(node -> node.getName()).get();

            if (actualName.equals(methodName)) {
                return methodEdge.getTo().getNode().get();
            }
        }
        return null;
    }

    private static Collection<Edge> findMatchingEdges(Node root, Function<Edge, Boolean> predicate, boolean recursive) {
        if (recursive) {
            return doFindMatchingEdges(root, predicate, new HashSet<Edge>());
        } else {
            return matchingEdgesFrom(root, predicate);
        }
    }
    private static Collection<Edge> doFindMatchingEdges(Node root, Function<Edge, Boolean> predicate, Set<Edge> visited) {
        var found = matchingEdgesFrom(root, predicate);
        for (var edge : root.getOutboundEdges()) {
            if (visited.contains(edge)) {
                continue;
            }
            visited.add(edge);
            if (edge.getTo().isResolved()) {
                found.addAll(doFindMatchingEdges(edge.getTo().getNode().get(), predicate, visited));
            }
        }
        return found;
    }
    private static Collection<Edge> matchingEdgesFrom(Node root, Function<Edge, Boolean> predicate) {
        Collection<Edge> edges = new ArrayList<>();
        for (var edge : root.getOutboundEdges()) {
            if (predicate.apply(edge)) {
                edges.add(edge);
            }
        }
        return edges;
    }

    private static Edge findFirstMatch(Node root, Function<Edge, Boolean> predicate) {
        return doFindFirstMatch(root, predicate, new HashSet<Edge>());
    }
    private static Edge doFindFirstMatch(Node root, Function<Edge, Boolean> predicate, Set<Edge> visited) {
        Edge found = null;
        for (var edge : root.getOutboundEdges()) {
            if (predicate.apply(edge)) {
                return edge;
            }
        }
        for (var edge : root.getOutboundEdges()) {
            if (visited.contains(edge)) {
                continue;
            }
            visited.add(edge);
            if (edge.getTo().isResolved()) {
                found = doFindFirstMatch(edge.getTo().getNode().get(), predicate, visited);
            }
            if (found != null)
                return found;
        }
        return found;
    }

    private static Function<Edge, Boolean> edgeAndnodeTypesAndNameFilter(EdgeType edgeType, NodeType type, String name) {
        return (edge) -> {
            if (edgeType != edge.getType()) {
                return false;
            }
            if (edge.getTo().isResolved()) {
                var n = edge.getTo().getNode().get();
                return n.getType() == type && n.getName().equals(name);
            } else {
                return false;
            }
        };
    }
    private static Function<Edge, Boolean> edgeAndNodeTypesFilter(EdgeType edgeType, NodeType type) {
        return (edge) -> {
            if (edgeType != edge.getType()) {
                return false;
            }
            if (edge.getTo().isResolved()) {
                var n = edge.getTo().getNode().get();
                return n.getType() == type;
            } else {
                return false;
            }
        };
    }

    private static AntlrImport importString(String text) {
        AntlrImport imp = new AntlrImport();
        var inStream  = CharStreams.fromString(text);
        imp.importSource(inStream);
        imp.postProcess();
        return imp;
    }
}
