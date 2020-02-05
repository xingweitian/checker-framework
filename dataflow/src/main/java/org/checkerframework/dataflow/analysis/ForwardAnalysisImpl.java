package org.checkerframework.dataflow.analysis;

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.UnderlyingAST;
import org.checkerframework.dataflow.cfg.UnderlyingAST.CFGLambda;
import org.checkerframework.dataflow.cfg.UnderlyingAST.CFGMethod;
import org.checkerframework.dataflow.cfg.UnderlyingAST.Kind;
import org.checkerframework.dataflow.cfg.block.Block;
import org.checkerframework.dataflow.cfg.block.ConditionalBlock;
import org.checkerframework.dataflow.cfg.block.ExceptionBlock;
import org.checkerframework.dataflow.cfg.block.RegularBlock;
import org.checkerframework.dataflow.cfg.block.SpecialBlock;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.ReturnNode;
import org.checkerframework.javacutil.BugInCF;
import org.checkerframework.javacutil.Pair;

/**
 * An implementation of a forward analysis to solve a org.checkerframework.dataflow problem given a
 * control flow graph and a transfer function.
 *
 * @param <V> The abstract value type to be tracked by the analysis
 * @param <S> The store type used in the analysis
 * @param <T> The transfer function type that is used to approximated runtime behavior
 */
public class ForwardAnalysisImpl<
                V extends AbstractValue<V>,
                S extends Store<S>,
                T extends ForwardTransferFunction<V, S>>
        extends AbstractAnalysis<V, S, T> implements ForwardAnalysis<V, S, T> {

    /**
     * Number of times each block has been analyzed since the last time widening was applied. Null
     * if maxCountBeforeWidening is -1, which implies widening isn't used for this analysis.
     */
    protected final @Nullable IdentityHashMap<Block, Integer> blockCount;

    /**
     * Number of times a block can be analyzed before widening. -1 implies that widening shouldn't
     * be used.
     */
    protected final int maxCountBeforeWidening;

    /** Then stores before every basic block (assumed to be 'no information' if not present). */
    protected final IdentityHashMap<Block, S> thenStores;

    /** Else stores before every basic block (assumed to be 'no information' if not present). */
    protected final IdentityHashMap<Block, S> elseStores;

    /** The stores after every return statement. */
    protected final IdentityHashMap<ReturnNode, TransferResult<V, S>> storesAtReturnStatements;

    /**
     * Construct an object that can perform a org.checkerframework.dataflow forward analysis over a
     * control flow graph. The transfer function is set by the subclass later.
     *
     * @param maxCountBeforeWidening number of times a block can be analyzed before widening
     */
    public ForwardAnalysisImpl(int maxCountBeforeWidening) {
        super(Direction.FORWARD);
        this.maxCountBeforeWidening = maxCountBeforeWidening;
        this.blockCount = maxCountBeforeWidening == -1 ? null : new IdentityHashMap<>();
        this.thenStores = new IdentityHashMap<>();
        this.elseStores = new IdentityHashMap<>();
        this.storesAtReturnStatements = new IdentityHashMap<>();
    }

    /**
     * Construct an object that can perform a org.checkerframework.dataflow forward analysis over a
     * control flow graph given a transfer function.
     *
     * @param transfer the transfer function
     */
    public ForwardAnalysisImpl(@Nullable T transfer) {
        this(-1);
        this.transferFunction = transfer;
    }

    @Override
    public void performAnalysis(ControlFlowGraph cfg) {
        if (isRunning) {
            throw new BugInCF(
                    "ForwardAnalysisImpl::performAnalysis() doesn't expected get called when analysis is running!");
        }
        isRunning = true;

        try {
            init(cfg);

            while (!worklist.isEmpty()) {
                Block b = worklist.poll();
                performAnalysisBlock(b);
            }
        } finally {
            assert isRunning;
            // In case preformatAnalysisBlock crashed, reset isRunning to false.
            isRunning = false;
        }
    }

    /** Perform the actual analysis on one block. */
    private void performAnalysisBlock(Block b) {
        switch (b.getType()) {
            case REGULAR_BLOCK:
                {
                    RegularBlock rb = (RegularBlock) b;

                    // Apply transfer function to contents
                    TransferInput<V, S> inputBefore = getInputBefore(rb);
                    assert inputBefore != null : "@AssumeAssertion(nullness): invariant";
                    currentInput = inputBefore.copy();
                    Node lastNode = null;
                    boolean addToWorklistAgain = false;
                    for (Node n : rb.getContents()) {
                        assert currentInput != null : "@AssumeAssertion(nullness): invariant";
                        TransferResult<V, S> transferResult = callTransferFunction(n, currentInput);
                        addToWorklistAgain |= updateNodeValues(n, transferResult);
                        currentInput = new TransferInput<>(n, this, transferResult);
                        lastNode = n;
                    }
                    assert currentInput != null : "@AssumeAssertion(nullness): invariant";
                    // Loop will run at least once, making transferResult non-null

                    // Propagate store to successors
                    Block succ = rb.getSuccessor();

                    assert succ != null
                            : "@AssumeAssertion(nullness): regular basic block without non-exceptional successor unexpected";

                    propagateStoresTo(
                            succ, lastNode, currentInput, rb.getFlowRule(), addToWorklistAgain);
                    break;
                }

            case EXCEPTION_BLOCK:
                {
                    ExceptionBlock eb = (ExceptionBlock) b;

                    // Apply transfer function to content
                    TransferInput<V, S> inputBefore = getInputBefore(eb);
                    assert inputBefore != null : "@AssumeAssertion(nullness): invariant";
                    currentInput = inputBefore.copy();
                    Node node = eb.getNode();
                    TransferResult<V, S> transferResult = callTransferFunction(node, currentInput);
                    boolean addToWorklistAgain = updateNodeValues(node, transferResult);

                    // Propagate store to successor
                    Block succ = eb.getSuccessor();
                    if (succ != null) {
                        currentInput = new TransferInput<>(node, this, transferResult);
                        // TODO: Variable wasn't used.
                        // Store.FlowRule storeFlow = eb.getFlowRule();
                        propagateStoresTo(
                                succ, node, currentInput, eb.getFlowRule(), addToWorklistAgain);
                    }

                    // Propagate store to exceptional successors
                    for (Entry<TypeMirror, Set<Block>> e :
                            eb.getExceptionalSuccessors().entrySet()) {
                        TypeMirror cause = e.getKey();
                        S exceptionalStore = transferResult.getExceptionalStore(cause);
                        if (exceptionalStore != null) {
                            for (Block exceptionSucc : e.getValue()) {
                                addStoreBefore(
                                        exceptionSucc,
                                        node,
                                        exceptionalStore,
                                        Store.Kind.BOTH,
                                        addToWorklistAgain);
                            }
                        } else {
                            for (Block exceptionSucc : e.getValue()) {
                                addStoreBefore(
                                        exceptionSucc,
                                        node,
                                        inputBefore.copy().getRegularStore(),
                                        Store.Kind.BOTH,
                                        addToWorklistAgain);
                            }
                        }
                    }
                    break;
                }

            case CONDITIONAL_BLOCK:
                {
                    ConditionalBlock cb = (ConditionalBlock) b;

                    // Get store before
                    TransferInput<V, S> inputBefore = getInputBefore(cb);
                    assert inputBefore != null : "@AssumeAssertion(nullness): invariant";
                    TransferInput<V, S> input = inputBefore.copy();

                    // Propagate store to successor
                    Block thenSucc = cb.getThenSuccessor();
                    Block elseSucc = cb.getElseSuccessor();

                    propagateStoresTo(thenSucc, null, input, cb.getThenFlowRule(), false);
                    propagateStoresTo(elseSucc, null, input, cb.getElseFlowRule(), false);
                    break;
                }

            case SPECIAL_BLOCK:
                {
                    // Special basic blocks are empty and cannot throw exceptions,
                    // thus there is no need to perform any analysis.
                    SpecialBlock sb = (SpecialBlock) b;
                    Block succ = sb.getSuccessor();
                    if (succ != null) {
                        TransferInput<V, S> input = getInputBefore(b);
                        assert input != null : "@AssumeAssertion(nullness): invariant";
                        propagateStoresTo(succ, null, input, sb.getFlowRule(), false);
                    }
                    break;
                }

            default:
                throw new BugInCF(
                        "ForwardAnalysisImpl::performAnalysis() unexpected block type: "
                                + b.getType());
        }
    }

    @Override
    public @Nullable TransferInput<V, S> getInput(Block b) {
        return getInputBefore(b);
    }

    @Override
    public List<Pair<ReturnNode, TransferResult<V, S>>> getReturnStatementStores() {
        assert cfg != null : "@AssumeAssertion(nullness): invariant";
        List<Pair<ReturnNode, TransferResult<V, S>>> result = new ArrayList<>();
        for (ReturnNode returnNode : cfg.getReturnNodes()) {
            TransferResult<V, S> store = storesAtReturnStatements.get(returnNode);
            result.add(Pair.of(returnNode, store));
        }
        return result;
    }

    @Override
    public S runAnalysisFor(
            Node node,
            boolean before,
            TransferInput<V, S> transferInput,
            IdentityHashMap<Node, V> nodeValues,
            Map<TransferInput<V, S>, IdentityHashMap<Node, TransferResult<V, S>>> analysisCaches) {
        Block block = node.getBlock();
        assert block != null : "@AssumeAssertion(nullness): invariant";
        Node oldCurrentNode = currentNode;

        // Prepare cache
        IdentityHashMap<Node, TransferResult<V, S>> cache;
        if (analysisCaches != null) {
            cache = analysisCaches.get(transferInput);
            if (cache == null) {
                cache = new IdentityHashMap<>();
                analysisCaches.put(transferInput, cache);
            }
        } else {
            cache = null;
        }

        // TODO: Understand why the Store of passing node is analysis.currentInput.getRegularStore()
        //  when the analysis is running
        if (isRunning) {
            assert currentInput != null : "@AssumeAssertion(nullness): invariant";
            return currentInput.getRegularStore();
        }
        setNodeValues(nodeValues);
        isRunning = true;
        try {
            switch (block.getType()) {
                case REGULAR_BLOCK:
                    {
                        RegularBlock rb = (RegularBlock) block;

                        // Apply transfer function to contents until
                        // we found the node we are looking for.
                        TransferInput<V, S> store = transferInput;
                        TransferResult<V, S> transferResult;
                        for (Node n : rb.getContents()) {
                            currentNode = n;
                            if (n == node && before) {
                                return store.getRegularStore();
                            }
                            if (cache != null && cache.containsKey(n)) {
                                transferResult = cache.get(n);
                            } else {
                                // Copy the store to preserve to change the state in the cache
                                transferResult = callTransferFunction(n, store.copy());
                                if (cache != null) {
                                    cache.put(n, transferResult);
                                }
                            }
                            if (n == node) {
                                return transferResult.getRegularStore();
                            }
                            store = new TransferInput<>(n, this, transferResult);
                        }
                        // This point should never be reached. If the block of 'node' is
                        // 'block', then 'node' must be part of the contents of 'block'.
                        throw new BugInCF(
                                "ForwardAnalysisImpl::runAnalysisFor() this point should never be reached!");
                    }

                case EXCEPTION_BLOCK:
                    {
                        ExceptionBlock eb = (ExceptionBlock) block;

                        // Apply transfer function to content
                        if (eb.getNode() != node) {
                            throw new BugInCF(
                                    "ForwardAnalysisImpl::runAnalysisFor() it is expected node is equal to the node"
                                            + "in excetion block, but get: node: "
                                            + node
                                            + "\teBlock.getNode(): "
                                            + eb.getNode());
                        }

                        if (before) {
                            return transferInput.getRegularStore();
                        }

                        currentNode = node;
                        TransferResult<V, S> transferResult =
                                callTransferFunction(node, transferInput);
                        return transferResult.getRegularStore();
                    }

                default:
                    // Only regular blocks and exceptional blocks can hold nodes.
                    throw new BugInCF(
                            "ForwardAnalysisImpl::runAnalysisFor() unexpected block type: "
                                    + block.getType());
            }

        } finally {
            currentNode = oldCurrentNode;
            isRunning = false;
        }
    }

    @Override
    protected void initFields(ControlFlowGraph cfg) {
        thenStores.clear();
        elseStores.clear();
        if (blockCount != null) {
            blockCount.clear();
        }
        storesAtReturnStatements.clear();
        super.initFields(cfg);
    }

    @Override
    protected void initInitialInputs() {
        worklist.process(cfg);
        worklist.add(cfg.getEntryBlock());

        List<LocalVariableNode> parameters = null;
        UnderlyingAST underlyingAST = cfg.getUnderlyingAST();
        if (underlyingAST.getKind() == Kind.METHOD) {
            MethodTree tree = ((CFGMethod) underlyingAST).getMethod();
            parameters = new ArrayList<>();
            for (VariableTree p : tree.getParameters()) {
                LocalVariableNode var = new LocalVariableNode(p);
                parameters.add(var);
                // TODO: document that LocalVariableNode has no block that it
                //  belongs to
            }
        } else if (underlyingAST.getKind() == Kind.LAMBDA) {
            LambdaExpressionTree lambda = ((CFGLambda) underlyingAST).getLambdaTree();
            parameters = new ArrayList<>();
            for (VariableTree p : lambda.getParameters()) {
                LocalVariableNode var = new LocalVariableNode(p);
                parameters.add(var);
                // TODO: document that LocalVariableNode has no block that it
                //  belongs to
            }
        }
        assert transferFunction != null : "@AssumeAssertion(nullness): invariant";
        S initialStore = transferFunction.initialStore(underlyingAST, parameters);
        Block entry = cfg.getEntryBlock();
        thenStores.put(entry, initialStore);
        elseStores.put(entry, initialStore);
        inputs.put(entry, new TransferInput<>(null, this, initialStore));
    }

    /**
     * Call the transfer function for node {@code node}, and set that node as current node first.
     */
    @Override
    protected TransferResult<V, S> callTransferFunction(Node node, TransferInput<V, S> input) {
        TransferResult<V, S> transferResult = super.callTransferFunction(node, input);

        if (node instanceof ReturnNode) {
            // Save a copy of the store to later check if some property holds at a given return
            // statement
            storesAtReturnStatements.put((ReturnNode) node, transferResult);
        }
        return transferResult;
    }

    /**
     * Propagate the stores in currentInput to the successor block, succ, according to the flowRule.
     */
    @Override
    protected void propagateStoresTo(
            Block succ,
            @Nullable Node node,
            TransferInput<V, S> currentInput,
            Store.FlowRule flowRule,
            boolean addToWorklistAgain) {
        switch (flowRule) {
            case EACH_TO_EACH:
                if (currentInput.containsTwoStores()) {
                    addStoreBefore(
                            succ,
                            node,
                            currentInput.getThenStore(),
                            Store.Kind.THEN,
                            addToWorklistAgain);
                    addStoreBefore(
                            succ,
                            node,
                            currentInput.getElseStore(),
                            Store.Kind.ELSE,
                            addToWorklistAgain);
                } else {
                    addStoreBefore(
                            succ,
                            node,
                            currentInput.getRegularStore(),
                            Store.Kind.BOTH,
                            addToWorklistAgain);
                }
                break;
            case THEN_TO_BOTH:
                addStoreBefore(
                        succ,
                        node,
                        currentInput.getThenStore(),
                        Store.Kind.BOTH,
                        addToWorklistAgain);
                break;
            case ELSE_TO_BOTH:
                addStoreBefore(
                        succ,
                        node,
                        currentInput.getElseStore(),
                        Store.Kind.BOTH,
                        addToWorklistAgain);
                break;
            case THEN_TO_THEN:
                addStoreBefore(
                        succ,
                        node,
                        currentInput.getThenStore(),
                        Store.Kind.THEN,
                        addToWorklistAgain);
                break;
            case ELSE_TO_ELSE:
                addStoreBefore(
                        succ,
                        node,
                        currentInput.getElseStore(),
                        Store.Kind.ELSE,
                        addToWorklistAgain);
                break;
        }
    }

    /**
     * Add a store before the basic block {@code b} by merging with the existing stores for that
     * location.
     */
    protected void addStoreBefore(
            Block b, @Nullable Node node, S s, Store.Kind kind, boolean addBlockToWorklist) {
        S thenStore = getStoreBefore(b, Store.Kind.THEN);
        S elseStore = getStoreBefore(b, Store.Kind.ELSE);
        boolean shouldWiden = false;

        if (blockCount != null) {
            Integer count = blockCount.get(b);
            if (count == null) {
                count = 0;
            }
            shouldWiden = count >= maxCountBeforeWidening;
            if (shouldWiden) {
                blockCount.put(b, 0);
            } else {
                blockCount.put(b, count + 1);
            }
        }

        switch (kind) {
            case THEN:
                {
                    // Update the then store
                    S newThenStore = mergeStores(s, thenStore, shouldWiden);
                    if (!newThenStore.equals(thenStore)) {
                        thenStores.put(b, newThenStore);
                        if (elseStore != null) {
                            inputs.put(b, new TransferInput<>(node, this, newThenStore, elseStore));
                            addBlockToWorklist = true;
                        }
                    }
                    break;
                }
            case ELSE:
                {
                    // Update the else store
                    S newElseStore = mergeStores(s, elseStore, shouldWiden);
                    if (!newElseStore.equals(elseStore)) {
                        elseStores.put(b, newElseStore);
                        if (thenStore != null) {
                            inputs.put(b, new TransferInput<>(node, this, thenStore, newElseStore));
                            addBlockToWorklist = true;
                        }
                    }
                    break;
                }
            case BOTH:
                if (thenStore == elseStore) {
                    // Currently there is only one regular store
                    S newStore = mergeStores(s, thenStore, shouldWiden);
                    if (!newStore.equals(thenStore)) {
                        thenStores.put(b, newStore);
                        elseStores.put(b, newStore);
                        inputs.put(b, new TransferInput<>(node, this, newStore));
                        addBlockToWorklist = true;
                    }
                } else {
                    boolean storeChanged = false;

                    S newThenStore = mergeStores(s, thenStore, shouldWiden);
                    if (!newThenStore.equals(thenStore)) {
                        thenStores.put(b, newThenStore);
                        storeChanged = true;
                    }

                    S newElseStore = mergeStores(s, elseStore, shouldWiden);
                    if (!newElseStore.equals(elseStore)) {
                        elseStores.put(b, newElseStore);
                        storeChanged = true;
                    }

                    if (storeChanged) {
                        inputs.put(b, new TransferInput<>(node, this, newThenStore, newElseStore));
                        addBlockToWorklist = true;
                    }
                }
        }

        if (addBlockToWorklist) {
            addToWorklist(b);
        }
    }

    /**
     * Merge two stores, possibly widening the result.
     *
     * @param newStore the new Store
     * @param previousStore the previous Store
     * @param shouldWiden should widen or not
     */
    private S mergeStores(S newStore, @Nullable S previousStore, boolean shouldWiden) {
        if (previousStore == null) {
            return newStore;
        } else if (shouldWiden) {
            return newStore.widenedUpperBound(previousStore);
        } else {
            return newStore.leastUpperBound(previousStore);
        }
    }

    /** @return the store corresponding to the location right before the basic block {@code b}. */
    protected @Nullable S getStoreBefore(Block b, Store.Kind kind) {
        switch (kind) {
            case THEN:
                return readFromStore(thenStores, b);
            case ELSE:
                return readFromStore(elseStores, b);
            default:
                throw new BugInCF("unexpected Store Kind: " + kind);
        }
    }

    /**
     * @return the transfer input corresponding to the location right before the basic block {@code
     *     b}.
     */
    protected @Nullable TransferInput<V, S> getInputBefore(Block b) {
        return inputs.get(b);
    }
}
