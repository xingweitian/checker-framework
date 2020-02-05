package org.checkerframework.dataflow.analysis;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.analysis.Store.FlowRule;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.UnderlyingAST;
import org.checkerframework.dataflow.cfg.block.Block;
import org.checkerframework.dataflow.cfg.block.BlockImpl;
import org.checkerframework.dataflow.cfg.block.ConditionalBlock;
import org.checkerframework.dataflow.cfg.block.ExceptionBlock;
import org.checkerframework.dataflow.cfg.block.RegularBlock;
import org.checkerframework.dataflow.cfg.block.SpecialBlock;
import org.checkerframework.dataflow.cfg.block.SpecialBlock.SpecialBlockType;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.ReturnNode;
import org.checkerframework.javacutil.BugInCF;

/**
 * An implementation of a backward analysis to solve a org.checkerframework.dataflow problem given a
 * control flow graph and a transfer function.
 *
 * @param <V> The abstract value type to be tracked by the analysis
 * @param <S> The store type used in the analysis
 * @param <T> The transfer function type that is used to approximated runtime behavior
 */
public class BackwardAnalysisImpl<
                V extends AbstractValue<V>,
                S extends Store<S>,
                T extends BackwardTransferFunction<V, S>>
        extends AbstractAnalysis<V, S, T> implements BackwardAnalysis<V, S, T> {

    /** Out stores after every basic block (assumed to be 'no information' if not present). */
    protected final IdentityHashMap<Block, S> outStores;

    /**
     * Exception store of an Exception Block, propagated by exceptional successors of its Exception
     * Block, and merged with the normal TransferResult.
     */
    protected final IdentityHashMap<ExceptionBlock, S> exceptionStores;

    /** The store before the entry block. */
    protected S storeAtEntry;

    /**
     * Construct an object that can perform a org.checkerframework.dataflow backward analysis over a
     * control flow graph. The transfer function is set by the subclass later.
     */
    public BackwardAnalysisImpl() {
        super(Direction.BACKWARD);
        this.outStores = new IdentityHashMap<>();
        this.exceptionStores = new IdentityHashMap<>();
    }

    /**
     * Construct an object that can perform a org.checkerframework.dataflow backward analysis over a
     * control flow graph given a transfer function.
     *
     * @param transfer the transfer function
     */
    public BackwardAnalysisImpl(@Nullable T transfer) {
        this();
        this.transferFunction = transfer;
    }

    @Override
    public void performAnalysis(ControlFlowGraph cfg) {
        if (isRunning) {
            throw new BugInCF(
                    "BackwardAnalysisImpl::performAnalysis() doesn't expected get called when analysis is running!");
        }

        isRunning = true;

        init(cfg);

        while (!worklist.isEmpty()) {
            Block block = worklist.poll();

            switch (block.getType()) {
                case REGULAR_BLOCK:
                    {
                        RegularBlock rBlock = (RegularBlock) block;

                        TransferInput<V, S> inputAfter = getInput(rBlock);
                        currentInput = inputAfter.copy();
                        Node firstNode = null;
                        boolean addToWorklistAgain = false;

                        List<Node> nodeList = rBlock.getContents();
                        ListIterator<Node> reverseIter = nodeList.listIterator(nodeList.size());

                        while (reverseIter.hasPrevious()) {
                            Node node = reverseIter.previous();
                            TransferResult<V, S> transferResult =
                                    callTransferFunction(node, currentInput);
                            addToWorklistAgain |= updateNodeValues(node, transferResult);
                            currentInput = new TransferInput<>(node, this, transferResult);
                            firstNode = node;
                        }

                        // Propagate store to predecessors
                        for (BlockImpl pred : rBlock.getPredecessors()) {
                            propagateStoresTo(
                                    pred,
                                    firstNode,
                                    currentInput,
                                    FlowRule.EACH_TO_EACH,
                                    addToWorklistAgain);
                        }
                        break;
                    }

                case EXCEPTION_BLOCK:
                    {
                        ExceptionBlock eBlock = (ExceptionBlock) block;

                        TransferInput<V, S> inputAfter = getInput(eBlock);
                        currentInput = inputAfter.copy();
                        Node node = eBlock.getNode();
                        TransferResult<V, S> transferResult =
                                callTransferFunction(node, currentInput);
                        boolean addToWorklistAgain = updateNodeValues(node, transferResult);

                        // Merge transferResult with exceptionStore if exist one
                        S exceptionStore = exceptionStores.get(eBlock);
                        S mergedStore =
                                exceptionStore != null
                                        ? transferResult
                                                .getRegularStore()
                                                .leastUpperBound(exceptionStore)
                                        : transferResult.getRegularStore();

                        for (BlockImpl pred : eBlock.getPredecessors()) {
                            addStoreAfter(pred, node, mergedStore, addToWorklistAgain);
                        }
                        break;
                    }

                case CONDITIONAL_BLOCK:
                    {
                        ConditionalBlock cBlock = (ConditionalBlock) block;

                        TransferInput<V, S> inputAfter = getInput(cBlock);
                        TransferInput<V, S> input = inputAfter.copy();

                        for (BlockImpl pred : cBlock.getPredecessors()) {
                            propagateStoresTo(pred, null, input, FlowRule.EACH_TO_EACH, false);
                        }
                        break;
                    }

                case SPECIAL_BLOCK:
                    {
                        // Special basic blocks are empty and cannot throw exceptions,
                        // thus there is no need to perform any analysis.
                        SpecialBlock sBlock = (SpecialBlock) block;
                        final SpecialBlockType sType = sBlock.getSpecialType();
                        // storage the store at entry
                        if (sType == SpecialBlockType.ENTRY) {
                            storeAtEntry = outStores.get(sBlock);
                        } else {
                            assert sType == SpecialBlockType.EXIT
                                    || sType == SpecialBlockType.EXCEPTIONAL_EXIT;
                            for (BlockImpl pred : sBlock.getPredecessors()) {
                                propagateStoresTo(
                                        pred, null, getInput(sBlock), FlowRule.EACH_TO_EACH, false);
                            }
                        }
                        break;
                    }

                default:
                    throw new BugInCF(
                            "BackwardAnalysisImpl::performAnalysis() unexpected block type: "
                                    + block.getType());
            }
        }

        if (!isRunning) {
            throw new BugInCF(
                    "BackwardAnalysisImpl::performAnalysis() when just finished the analysis loop"
                            + " on Worklist, isRunning flag is expected to be true!");
        }
        isRunning = false;
    }

    @Override
    public TransferInput<V, S> getInput(Block b) {
        return inputs.get(b);
    }

    @Override
    public S getEntryStore() {
        return storeAtEntry;
    }

    @Override
    protected void initFields(ControlFlowGraph cfg) {
        super.initFields(cfg);
        outStores.clear();
        exceptionStores.clear();
        // storeAtEntry is null before analysis begin
        storeAtEntry = null;
    }

    @Override
    protected void initInitialInputs() {
        worklist.process(cfg);
        SpecialBlock regularExitBlock = cfg.getRegularExitBlock();
        SpecialBlock exceptionExitBlock = cfg.getExceptionalExitBlock();

        if (worklist.depthFirstOrder.get(regularExitBlock) == null
                && worklist.depthFirstOrder.get(exceptionExitBlock) == null) {
            throw new BugInCF(
                    "regularExitBlock and exceptionExitBlock should never both be null at the same time.");
        }

        UnderlyingAST underlyingAST = cfg.getUnderlyingAST();
        List<ReturnNode> returnNodes = cfg.getReturnNodes();

        S normalInitialStore = transferFunction.initialNormalExitStore(underlyingAST, returnNodes);
        S exceptionalInitialStore = transferFunction.initialExceptionalExitStore(underlyingAST);

        // exceptionExitBlock and regularExitBlock will always be non-null, as
        // CFGBuilder#CFGTranslationPhaseTwo#process() will always create these two exit blocks on a
        // CFG whether it has exit blocks or not according to the underlying AST.
        // Here the workaround is using the inner protected Map in Worklist to decide whether a
        // given cfg has a regularExitBlock and/or an exceptionExitBlock
        if (worklist.depthFirstOrder.get(regularExitBlock) != null) {
            worklist.add(regularExitBlock);
            inputs.put(regularExitBlock, new TransferInput<>(null, this, normalInitialStore));
            outStores.put(regularExitBlock, normalInitialStore);
        }

        if (worklist.depthFirstOrder.get(exceptionExitBlock) != null) {
            worklist.add(exceptionExitBlock);
            inputs.put(
                    exceptionExitBlock, new TransferInput<>(null, this, exceptionalInitialStore));
            outStores.put(exceptionExitBlock, exceptionalInitialStore);
        }

        if (worklist.isEmpty()) {
            throw new BugInCF(
                    "BackwardAnalysisImpl::initInitialInputs() worklist should has at least one exit block as start point.");
        }

        if (inputs.isEmpty() || outStores.isEmpty()) {
            throw new BugInCF(
                    "BackwardAnalysisImpl::initInitialInputs() should has at least one input and outStore at beginning");
        }
    }

    @Override
    protected void propagateStoresTo(
            Block pred,
            Node node,
            TransferInput<V, S> currentInput,
            FlowRule flowRule,
            boolean addToWorklistAgain) {
        if (flowRule != FlowRule.EACH_TO_EACH) {
            throw new BugInCF(
                    "backward analysis always propagate EACH to EACH, because there is no control flow.");
        }

        addStoreAfter(pred, node, currentInput.getRegularStore(), addToWorklistAgain);
    }

    /**
     * Add a store after the basic block {@code pred} by merging with the existing stores for that
     * location.
     */
    protected void addStoreAfter(Block pred, Node node, S s, boolean addBlockToWorklist) {
        // If Block {@code pred} is an ExceptionBlock, decide whether the
        // block of passing node is an exceptional successor of Block {@code pred}
        if (pred instanceof ExceptionBlock
                && ((ExceptionBlock) pred).getSuccessor() != null
                && node != null
                && (((ExceptionBlock) pred).getSuccessor().getId() == node.getBlock().getId())) {
            // If the block of passing node is an exceptional successor of Block {@code pred},
            // propagate store to the {@code exceptionStores}

            // Currently it doesn't track the label of an exceptional edge from Exception Block to
            // its exceptional successors in backward direction, instead, all exception stores of
            // exceptional successors of an Exception Block will merge to one exception store at the
            // Exception Block

            ExceptionBlock ebPred = (ExceptionBlock) pred;

            S exceptionStore = exceptionStores.get(ebPred);

            S newExceptionStore = (exceptionStore != null) ? exceptionStore.leastUpperBound(s) : s;
            if (!newExceptionStore.equals(exceptionStore)) {
                exceptionStores.put(ebPred, newExceptionStore);
                addBlockToWorklist = true;
            }
        } else {
            S predOutStore = getStoreAfter(pred);

            S newPredOutStore = (predOutStore != null) ? predOutStore.leastUpperBound(s) : s;

            if (!newPredOutStore.equals(predOutStore)) {
                outStores.put(pred, newPredOutStore);
                inputs.put(pred, new TransferInput<>(node, this, newPredOutStore));
                addBlockToWorklist = true;
            }
        }

        if (addBlockToWorklist) {
            addToWorklist(pred);
        }
    }

    /** @return the store corresponding to the location right after the basic block {@code b}. */
    protected S getStoreAfter(Block b) {
        return readFromStore(outStores, b);
    }

    @Override
    public S runAnalysisFor(
            Node node,
            boolean before,
            TransferInput<V, S> transferInput,
            IdentityHashMap<Node, V> nodeValues,
            Map<TransferInput<V, S>, IdentityHashMap<Node, TransferResult<V, S>>> analysisCaches) {
        Block block = node.getBlock();
        Node oldCurrentNode = currentNode;

        // TODO: Understand why the Store of passing node is analysis.currentInput.getRegularStore()
        // when the analysis is running
        if (isRunning) {
            return currentInput.getRegularStore();
        }

        isRunning = true;
        try {
            switch (block.getType()) {
                case REGULAR_BLOCK:
                    {
                        RegularBlock rBlock = (RegularBlock) block;

                        // Apply transfer function to contents until we found the node we are
                        // looking for.
                        TransferInput<V, S> store = transferInput;

                        List<Node> nodeList = rBlock.getContents();
                        ListIterator<Node> reverseIter = nodeList.listIterator(nodeList.size());

                        while (reverseIter.hasPrevious()) {
                            Node n = reverseIter.previous();
                            currentNode = n;
                            if (n == node && !before) {
                                return store.getRegularStore();
                            }
                            TransferResult<V, S> transferResult = callTransferFunction(n, store);
                            if (n == node) {
                                return transferResult.getRegularStore();
                            }
                            store = new TransferInput<>(n, this, transferResult);
                        }
                        // This point should never be reached. If the block of 'node' is
                        // 'block', then 'node' must be part of the contents of 'block'.
                        throw new BugInCF(
                                "BackwardAnalysisImpl::runAnalysisFor() This point should never be reached!");
                    }

                case EXCEPTION_BLOCK:
                    {
                        ExceptionBlock eBlock = (ExceptionBlock) block;

                        if (eBlock.getNode() != node) {
                            throw new BugInCF(
                                    "BackwardAnalysisImpl::runAnalysisFor() it is expected node is equal to the node"
                                            + "in exception block, but get: node: "
                                            + node
                                            + "\teBlock.getNode(): "
                                            + eBlock.getNode());
                        }

                        if (!before) {
                            return transferInput.getRegularStore();
                        }

                        currentNode = node;
                        TransferResult<V, S> transferResult =
                                callTransferFunction(node, transferInput);

                        // Merge transfer result with the exception store of this exceptional block
                        S exceptionStore = exceptionStores.get(eBlock);
                        return exceptionStore == null
                                ? transferResult.getRegularStore()
                                : transferResult.getRegularStore().leastUpperBound(exceptionStore);
                    }

                    // Only regular blocks and exceptional blocks can hold nodes.
                default:
                    throw new BugInCF(
                            "BackwardAnalysisImpl::runAnalysisFor() unexpected block type: "
                                    + block.getType());
            }

        } finally {
            currentNode = oldCurrentNode;
            isRunning = false;
        }
    }
}
