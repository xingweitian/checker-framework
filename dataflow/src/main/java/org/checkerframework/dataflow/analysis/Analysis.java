package org.checkerframework.dataflow.analysis;

import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.block.Block;
import org.checkerframework.dataflow.cfg.node.Node;

/**
 * General Dataflow Analysis Interface. This interface defines general behaviors of a data-flow
 * analysis, given a control flow graph and a transfer function. A data-flow analysis should only
 * has one direction, either forward or backward. The direction of corresponding transfer function
 * should be consistent with the analysis, i.e. a forward analysis should be given a forward
 * transfer function, and a backward analysis should be given a backward transfer function.
 *
 * @param <V> the abstract value type to be tracked by the analysis
 * @param <S> the store type used in the analysis
 * @param <T> the transfer function type that is used to approximated runtime behavior
 */
public interface Analysis<
        V extends AbstractValue<V>, S extends Store<S>, T extends TransferFunction<V, S>> {

    /**
     * The direction of an analysis instance. An analysis could either be a forward analysis with
     * FORWARD direction, or a backward analysis with BACKWARD direction.
     */
    enum Direction {
        FORWARD,
        BACKWARD
    }

    /**
     * Get the direction of this analysis.
     *
     * @return the direction of this analysis
     */
    Direction getDirection();

    /**
     * Get the status of the analysis that whether it is currently running.
     *
     * @return true if the analysis is running currently
     */
    boolean isRunning();

    /**
     * Perform the actual analysis. Should only be called once after the analysis instance has been
     * created.
     */
    void performAnalysis(ControlFlowGraph cfg);

    /**
     * Get the analysis result of this analysis. Should only be called after the analysis has been
     * performed.
     *
     * @return the result of a org.checkerframework.dataflow analysis
     */
    AnalysisResult<V, S> getResult();

    /**
     * Get the transfer function of this analysis.
     *
     * @return the transfer function of this analysis
     */
    T getTransferFunction();

    /**
     * Set a transfer function for this analysis.
     *
     * @param transferFunction the transfer function set to this analysis
     */
    void setTransferFunction(T transferFunction);

    /**
     * Get the transfer input of a given {@link Block} b.
     *
     * @param b a given Block
     * @return the transfer input of this Block
     */
    TransferInput<V, S> getInput(Block b);

    /**
     * @param n a {@link Node}
     * @return the abstract value for {@link Node} {@code n}, or {@code null} if no information is
     *     available. Note that if the analysis has not finished yet, this value might not represent
     *     the final value for this node.
     */
    V getValue(Node n);

    /**
     * Get the regular exit store of this analysis.
     *
     * @return the regular exit store, or {@code null} if there is no such store (because the method
     *     cannot exit through the regular exit block).
     */
    S getRegularExitStore();

    /**
     * Get the exceptional exit store of this analysis.
     *
     * @return the exceptional exit store, or {@code null} if there is no such store
     */
    S getExceptionalExitStore();
}
