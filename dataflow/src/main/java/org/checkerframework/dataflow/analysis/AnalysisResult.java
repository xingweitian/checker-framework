package org.checkerframework.dataflow.analysis;

import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.lang.model.element.Element;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.cfg.block.Block;
import org.checkerframework.dataflow.cfg.block.ExceptionBlock;
import org.checkerframework.dataflow.cfg.block.RegularBlock;
import org.checkerframework.dataflow.cfg.node.AssignmentNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.javacutil.BugInCF;

/**
 * An {@link AnalysisResult} represents the result of a org.checkerframework.dataflow analysis by
 * providing the abstract values given a node or a tree. Note that it does not keep track of custom
 * results computed by some analysis.
 *
 * @param <A> type of the abstract value that is tracked
 */
public class AnalysisResult<A extends AbstractValue<A>, S extends Store<S>> {

    /** Abstract values of nodes. */
    protected final IdentityHashMap<Node, A> nodeValues;

    /** Map from AST {@link Tree}s to sets of {@link Node}s. */
    protected final IdentityHashMap<Tree, Set<Node>> treeLookup;

    /** Map from AST {@link UnaryTree}s to corresponding {@link AssignmentNode}s. */
    protected final IdentityHashMap<UnaryTree, AssignmentNode> unaryAssignNodeLookup;

    /** Map from (effectively final) local variable elements to their abstract value. */
    protected final HashMap<Element, A> finalLocalValues;

    /** The stores before every method call. */
    protected final IdentityHashMap<Block, TransferInput<A, S>> stores;

    /**
     * Caches of the analysis results for each input for the block of the node and each node.
     *
     * @see #runAnalysisFor(Node, boolean, TransferInput, IdentityHashMap, Map)
     */
    protected final Map<TransferInput<A, S>, IdentityHashMap<Node, TransferResult<A, S>>>
            analysisCaches;

    /**
     * Initialize with given mappings.
     *
     * @param nodeValues abstract values of nodes
     * @param stores a map from blocks to transfer inputs
     * @param treeLookup a map from abstract syntax trees to sets of nodes
     * @param unaryAssignNodeLookup a map from abstract syntax trees to corresponding assignment
     *     nodes
     * @param finalLocalValues a map from (effectively final) local variable elements to their
     *     abstract value
     * @param analysisCaches caches of analysis results
     */
    protected AnalysisResult(
            Map<Node, A> nodeValues,
            IdentityHashMap<Block, TransferInput<A, S>> stores,
            IdentityHashMap<Tree, Set<Node>> treeLookup,
            IdentityHashMap<UnaryTree, AssignmentNode> unaryAssignNodeLookup,
            HashMap<Element, A> finalLocalValues,
            Map<TransferInput<A, S>, IdentityHashMap<Node, TransferResult<A, S>>> analysisCaches) {
        this.nodeValues = new IdentityHashMap<>(nodeValues);
        this.treeLookup = new IdentityHashMap<>(treeLookup);
        this.unaryAssignNodeLookup = new IdentityHashMap<>(unaryAssignNodeLookup);
        // TODO: why are stores and finalLocalValues captured?
        this.stores = stores;
        this.finalLocalValues = finalLocalValues;
        this.analysisCaches = analysisCaches;
    }

    /**
     * Initialize with given mappings and empty cache.
     *
     * @param nodeValues abstract values of nodes
     * @param stores a map from blocks to transfer inputs
     * @param treeLookup a map from abstract syntax trees to sets of nodes
     * @param unaryAssignNodeLookup a map from abstract syntax trees to corresponding assignment
     *     nodes
     * @param finalLocalValues a map from (effectively final) local variable elements to their
     *     abstract value
     */
    public AnalysisResult(
            Map<Node, A> nodeValues,
            IdentityHashMap<Block, TransferInput<A, S>> stores,
            IdentityHashMap<Tree, Set<Node>> treeLookup,
            IdentityHashMap<UnaryTree, AssignmentNode> unaryAssignNodeLookup,
            HashMap<Element, A> finalLocalValues) {
        this(
                nodeValues,
                stores,
                treeLookup,
                unaryAssignNodeLookup,
                finalLocalValues,
                new IdentityHashMap<>());
    }

    /**
     * Initialize empty result with specified cache.
     *
     * @param analysisCaches caches of analysis results
     */
    public AnalysisResult(
            Map<TransferInput<A, S>, IdentityHashMap<Node, TransferResult<A, S>>> analysisCaches) {
        this(
                new IdentityHashMap<>(),
                new IdentityHashMap<>(),
                new IdentityHashMap<>(),
                new IdentityHashMap<>(),
                new HashMap<>(),
                analysisCaches);
    }

    /**
     * Combine with another analysis result.
     *
     * @param other another analysis result waiting for being merged
     */
    public void combine(AnalysisResult<A, S> other) {
        nodeValues.putAll(other.nodeValues);
        mergeTreeLookup(treeLookup, other.treeLookup);
        unaryAssignNodeLookup.putAll(other.unaryAssignNodeLookup);
        stores.putAll(other.stores);
        finalLocalValues.putAll(other.finalLocalValues);
    }

    /**
     * Merge all entries from otherTreeLookup into treeLookup. Merge sets if already present.
     *
     * @param treeLookup a map from abstract syntax trees to sets of nodes
     * @param otherTreeLookup another treeLookup waiting for being merged into {@code treeLookup}
     */
    private static void mergeTreeLookup(
            IdentityHashMap<Tree, Set<Node>> treeLookup,
            IdentityHashMap<Tree, Set<Node>> otherTreeLookup) {
        for (Entry<Tree, Set<Node>> entry : otherTreeLookup.entrySet()) {
            Set<Node> hit = treeLookup.get(entry.getKey());
            if (hit == null) {
                treeLookup.put(entry.getKey(), entry.getValue());
            } else {
                hit.addAll(entry.getValue());
            }
        }
    }

    /** @return the value of effectively final local variables */
    public HashMap<Element, A> getFinalLocalValues() {
        return finalLocalValues;
    }

    /**
     * Return the abstract value for {@link Node} {@code n}, or {@code null} if no information is
     * available.
     *
     * @param n the given node
     * @return the abstract value of the given node
     */
    public @Nullable A getValue(Node n) {
        return nodeValues.get(n);
    }

    /**
     * Return the abstract value for {@link Tree} {@code t}, or {@code null} if no information is
     * available.
     *
     * @param t the given tree
     * @return the abstract value of the given tree
     */
    public @Nullable A getValue(Tree t) {
        Set<Node> nodes = treeLookup.get(t);

        if (nodes == null) {
            return null;
        }
        A merged = null;
        for (Node aNode : nodes) {
            A a = getValue(aNode);
            if (merged == null) {
                merged = a;
            } else if (a != null) {
                merged = merged.leastUpperBound(a);
            }
        }
        return merged;
    }

    /**
     * Returns the {@code Node}s corresponding to a particular {@code Tree}. Multiple {@code Node}s
     * can correspond to a single {@code Tree} because of several reasons:
     *
     * <ol>
     *   <li>In a lambda expression such as {@code () -> 5} the {@code 5} is both an {@code
     *       IntegerLiteralNode} and a {@code LambdaResultExpressionNode}.
     *   <li>Narrowing and widening primitive conversions can result in {@code
     *       NarrowingConversionNode} and {@code WideningConversionNode}.
     *   <li>Automatic String conversion can result in a {@code StringConversionNode}.
     *   <li>Trees for {@code finally} blocks are cloned to achieve a precise CFG. Any {@code Tree}
     *       within a finally block can have multiple corresponding {@code Node}s attached to them.
     * </ol>
     *
     * Callers of this method should always iterate through the returned set, possibly ignoring all
     * {@code Node}s they are not interested in.
     *
     * @param tree the given tree
     * @return the set of {@link Node}s for a given {@link Tree}
     */
    public @Nullable Set<Node> getNodesForTree(Tree tree) {
        return treeLookup.get(tree);
    }

    /**
     * Return the corresponding {@link AssignmentNode} for a given {@link UnaryTree}.
     *
     * @param tree the given unary tree
     * @return the corresponding assignment node
     */
    public AssignmentNode getAssignForUnaryTree(UnaryTree tree) {
        assert unaryAssignNodeLookup.containsKey(tree) : tree + " is not in unaryAssignNodeLookup";
        return unaryAssignNodeLookup.get(tree);
    }

    /**
     * Return the store immediately before a given tree.
     *
     * @param tree the given tree
     * @return the store before the given tree
     */
    public @Nullable S getStoreBefore(Tree tree) {
        Set<Node> nodes = getNodesForTree(tree);
        if (nodes == null) {
            return null;
        }
        S merged = null;
        for (Node node : nodes) {
            S s = getStoreBefore(node);
            if (merged == null) {
                merged = s;
            } else if (s != null) {
                merged = merged.leastUpperBound(s);
            }
        }
        return merged;
    }

    /**
     * Return the store immediately before a given node.
     *
     * @param node the give node
     * @return the store before the given node
     */
    public @Nullable S getStoreBefore(Node node) {
        return runAnalysisFor(node, true);
    }

    /**
     * Return the regular store immediately before a given {@link Block}.
     *
     * @param bb the given block
     */
    public S getStoreBefore(Block bb) {
        TransferInput<A, S> transferInput = stores.get(bb);
        AbstractAnalysis<A, S, ?> analysis = transferInput.analysis;
        switch (analysis.getDirection()) {
            case FORWARD:
                {
                    return transferInput.getRegularStore();
                }
            case BACKWARD:
                {
                    Node firstNode;
                    switch (bb.getType()) {
                        case REGULAR_BLOCK:
                            firstNode = ((RegularBlock) bb).getContents().get(0);
                            break;
                        case EXCEPTION_BLOCK:
                            firstNode = ((ExceptionBlock) bb).getNode();
                            break;
                        default:
                            firstNode = null;
                    }
                    if (firstNode == null) {
                        // This block doesn't contains any node, return store in transfer input
                        return transferInput.getRegularStore();
                    }
                    return analysis.runAnalysisFor(
                            firstNode, true, transferInput, nodeValues, analysisCaches);
                }
            default:
                {
                    throw new BugInCF(
                            "AnalysisResult::getStoreAfter: unknown direction: "
                                    + analysis.getDirection());
                }
        }
    }

    /**
     * Return the regular store immediately after a given block.
     *
     * @param bb the given block
     * @return the store after the given block
     */
    public S getStoreAfter(Block bb) {
        TransferInput<A, S> transferInput = stores.get(bb);
        AbstractAnalysis<A, S, ?> analysis = transferInput.analysis;
        switch (analysis.getDirection()) {
            case FORWARD:
                {
                    Node lastNode;
                    switch (bb.getType()) {
                        case REGULAR_BLOCK:
                            List<Node> blockContents = ((RegularBlock) bb).getContents();
                            lastNode = blockContents.get(blockContents.size() - 1);
                            break;
                        case EXCEPTION_BLOCK:
                            lastNode = ((ExceptionBlock) bb).getNode();
                            break;
                        default:
                            lastNode = null;
                    }
                    if (lastNode == null) {
                        // This block doesn't contains any node, return store in transfer input
                        return transferInput.getRegularStore();
                    }
                    return analysis.runAnalysisFor(
                            lastNode, false, transferInput, nodeValues, analysisCaches);
                }
            case BACKWARD:
                {
                    return transferInput.getRegularStore();
                }
            default:
                {
                    throw new BugInCF(
                            "AnalysisResult::getStoreAfter: unknown direction: "
                                    + analysis.getDirection());
                }
        }
    }

    /**
     * Return the store immediately after a given tree.
     *
     * @param tree the given tree
     * @return the store after the given tree
     */
    public @Nullable S getStoreAfter(Tree tree) {
        Set<Node> nodes = getNodesForTree(tree);
        if (nodes == null) {
            return null;
        }
        S merged = null;
        for (Node node : nodes) {
            S s = getStoreAfter(node);
            if (merged == null) {
                merged = s;
            } else if (s != null) {
                merged = merged.leastUpperBound(s);
            }
        }
        return merged;
    }

    /**
     * Return the store immediately after a given node.
     *
     * @param node the given node
     * @return the store after the given node
     */
    public @Nullable S getStoreAfter(Node node) {
        return runAnalysisFor(node, false);
    }

    /**
     * Runs the analysis again within the block of {@code node} and returns the store at the
     * location of {@code node}. If {@code before} is true, then the store immediately before the
     * {@link Node} {@code node} is returned. Otherwise, the store after {@code node} is returned.
     *
     * <p>If the given {@link Node} cannot be reached (in the control flow graph), then {@code null}
     * is returned.
     *
     * @param node the node to analyze
     * @param before the boolean value to indicate which store to return
     * @return the store at the location of node after running the analysis
     */
    protected @Nullable S runAnalysisFor(Node node, boolean before) {
        Block block = node.getBlock();
        assert block != null : "@AssumeAssertion(nullness): invariant";
        TransferInput<A, S> transferInput = stores.get(block);
        if (transferInput == null) {
            return null;
        }
        return runAnalysisFor(node, before, transferInput, nodeValues, analysisCaches);
    }

    /**
     * Runs the analysis again within the block of {@code node} and returns the store at the
     * location of {@code node}. If {@code before} is true, then the store immediately before the
     * {@link Node} {@code node} is returned. Otherwise, the store after {@code node} is returned.
     * If {@code analysisCaches} is not null, this method uses a cache. {@code analysisCaches} is a
     * map to a cache for analysis result from an input of the block of the node. If the cache for
     * {@code transferInput} is not in {@code analysisCaches}, this method create new cache and
     * store it in {@code analysisCaches}. The cache is a map from a node to the analysis result of
     * the node.
     *
     * @param node the node to analyze
     * @param before the boolean value to indicate which store to return
     * @param transferInput the transfer input of the block of this node
     * @param nodeValues abstract values of nodes
     * @param analysisCaches caches of analysis results
     * @return the store at the location of node after running the analysis
     */
    public static <A extends AbstractValue<A>, S extends Store<S>> S runAnalysisFor(
            Node node,
            boolean before,
            TransferInput<A, S> transferInput,
            IdentityHashMap<Node, A> nodeValues,
            Map<TransferInput<A, S>, IdentityHashMap<Node, TransferResult<A, S>>> analysisCaches) {
        if (transferInput.analysis == null) {
            throw new BugInCF("transferInput contains null analysis!");
        }
        return transferInput.analysis.runAnalysisFor(
                node, before, transferInput, nodeValues, analysisCaches);
    }
}
