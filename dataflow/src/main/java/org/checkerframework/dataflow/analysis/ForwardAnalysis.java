package org.checkerframework.dataflow.analysis;

import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.cfg.node.ReturnNode;
import org.checkerframework.javacutil.Pair;

/**
 * General dataflow forward analysis interface. This sub-interface of {@link Analysis} defines the
 * general behaviors of a forward analysis, given a control flow graph and a forward transfer
 * function.
 *
 * @param <V> the abstract value type to be tracked by the analysis
 * @param <S> the store type used in the analysis
 * @param <T> the forward transfer function type that is used to approximated runtime behavior
 */
public interface ForwardAnalysis<
                V extends AbstractValue<V>,
                S extends Store<S>,
                T extends ForwardTransferFunction<V, S>>
        extends Analysis<V, S, T> {

    /**
     * Get stores at return statements. These stores are transfer results at return node. Thus for a
     * forward analysis, these stores contains the analyzed flow information from entry nodes to
     * return nodes.
     *
     * @return a list of {@code Pair} which each {@code Pair} is a {@link ReturnNode} and its
     *     corresponding {@link TransferResult}
     */
    List<Pair<ReturnNode, @Nullable TransferResult<V, S>>> getReturnStatementStores();
}
