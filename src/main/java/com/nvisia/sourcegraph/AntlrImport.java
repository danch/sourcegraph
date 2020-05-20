package com.nvisia.sourcegraph;

import com.nvisia.sourcegraph.antlr.Java9Lexer;
import com.nvisia.sourcegraph.antlr.Java9Parser;
import com.nvisia.sourcegraph.graph.Edge;
import com.nvisia.sourcegraph.graph.EdgeType;
import com.nvisia.sourcegraph.graph.Node;
import com.nvisia.sourcegraph.graph.NodeVisitor;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public class AntlrImport {
    public static int SPACES_PER_TREE_LEVEL = 2;

    private GraphTranslator translator;

    public AntlrImport() {
        translator = new GraphTranslator();
    }

    public void importSource(CharStream input) {
        Java9Lexer lexer = new Java9Lexer(input);
        CommonTokenStream tokens = new CommonTokenStream((TokenSource) lexer);
        Java9Parser parser = new Java9Parser(tokens);
        Java9Parser.CompilationUnitContext tree = parser.compilationUnit(); // parse a compilationUnit

        ParseTreeWalker.DEFAULT.walk(translator, tree);  // initiate walk of tree with listener in use of default walker
    }

    public Collection<Node> getTopLevelNodes() {
        return translator.getTopLevelNodes();
    }

    public void postProcess() {
        Collection<Node> topLevelNodes = translator.getTopLevelNodes();
        var typeCache = translator.getTypeCache();
        for (Node top : topLevelNodes) {
            top.preOrderTraverse(Optional.of(EdgeType.Contains), (maybeEdgeType, nodeRef, level) -> {
                nodeRef.getNode().map(node -> {
                    node.getOutboundEdges().forEach(edge -> {
                        edge.resolveNodeRefs(typeCache);
                    });
                    return node;
                });
            });
        }
    }

    public void preOrderTraverse(Optional<EdgeType> maybeType,  NodeVisitor visitor) {
        Collection<Node> topLevelNodes = translator.getTopLevelNodes();
        for (Node top : topLevelNodes) {
            top.preOrderTraverse(maybeType, visitor);
        }
    }
    public void preOrderEdgeTraverse(Optional<EdgeType> maybeType, Consumer<Edge> visitor) {
        Collection<Node> topLevelNodes = translator.getTopLevelNodes();
        for (Node top : topLevelNodes) {
            top.preOrderEdgeTraverse(maybeType, visitor);
        }
    }

    public void textDumpContainsGraph() {
        preOrderTraverse(Optional.of(EdgeType.Contains),
                (maybeEdgeType, node, level) -> {
                    System.out.println(nSpaces(level * SPACES_PER_TREE_LEVEL) + maybeEdgeType.map(Enum::toString).orElse("") + "->" + node.toString());
                });
    }
    /* TODO this sort of functionality really needs a different home
    *   Also: Can't wait to feed this thing to itself: If this doesn't have a high CCN, we have a problem.
    *   TODO: fix and remove the above note. */
    public String toDOT() {
        var stringWriter = new StringWriter();
        var writer = new java.io.PrintWriter(stringWriter);
        writer.println("digraph danch_o_graph {");
        Set<String> visitedPaths = new HashSet<String>();
        preOrderEdgeTraverse(Optional.empty(),
                (edge) -> {
                    edge.getFrom().getNode().ifPresentOrElse(
                            node -> AntlrImport.emitNodeDescription(node, visitedPaths, writer)
                    , () -> {
                        int bpFodder = 0;
                    });
                    edge.getTo().getNode().ifPresentOrElse(
                            node -> AntlrImport.emitNodeDescription(node, visitedPaths, writer),
                            () -> emitStubDescription(edge.getTo().getNodePath(), visitedPaths, writer)
                    );
                    writer.println("\"" + edge.getFrom().getNodePath() + "\"" + "->" + "\"" + edge.getTo().getNodePath() + "\"" + " [label=\"" + edge.getType().toString() + "\"];");
                }
        );
        writer.println("}");
        writer.flush();
        return stringWriter.toString();
    }

    private static void emitNodeDescription(Node node, Set<String> visitedPaths, PrintWriter writer) {
        if (!visitedPaths.contains(node.getPath())) {
            visitedPaths.add(node.getPath());
            writer.println("node [label=\"" + node.getType() + ":" + node.getName() + "\"]; \"" + node.getPath() + "\";");
        }
    }

    private static void emitStubDescription(String nodePath, Set<String> visitedPaths, PrintWriter writer) {
        if (!visitedPaths.contains(nodePath)) {
            visitedPaths.add(nodePath);
            writer.println("node [label=\"Stub:"+nodePath+"\"]; \"" + nodePath + "\";");
        }
    }

    public static void main(String args[]) throws IOException  {

        AntlrImport antlrImport = new AntlrImport();

        //C:\Users\danch\source\importer\src\main\java
        Path inputDir = Path.of("src/test_source/java");
        List<String> allJavaFiles = getAllJavaFiles(inputDir);

        for (String file: allJavaFiles) {
            CharStream input = CharStreams.fromPath(Path.of(file));
            antlrImport.importSource(input);
        }

        antlrImport.postProcess();
        antlrImport.textDumpContainsGraph();
    }

    static List<String> getAllJavaFiles(Path inputDir) {
        File[] theseFiles = inputDir.toFile().listFiles((File file) ->
            file.isDirectory() || file.getName().endsWith("java"));
        List<String> fileNames = new LinkedList<String>();
        for (int i=0;i<theseFiles.length;i++) {
            if (theseFiles[i].isDirectory()) {
                List<String> childList = getAllJavaFiles(theseFiles[i].toPath());
                fileNames.addAll(childList);
            } else {
                fileNames.add(theseFiles[i].toPath().toAbsolutePath().toString());
            }
        }
        return fileNames;
    }
    private static String nSpaces(int i) {
        //yeah, could be cached, but this is temporary
        char[] raw = new char[i];
        Arrays.fill(raw, ' ');
        return new String(raw);
    }
}
