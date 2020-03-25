package org.checkerframework.dataflow.analysis;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.cfg.block.SpecialBlock;

/**
 * General dataflow backward analysis interface. This sub-interface of {@link Analysis} defines the
 * general behaviors of a backward analysis, given a control flow graph and a backward transfer
 * function.
 *
 * @param <V> The abstract value type to be tracked by the analysis
 * @param <S> The store type used in the analysis
 * @param <T> The forward transfer function type that is used to approximated runtime behavior
 */
public interface BackwardAnalysis<
                V extends AbstractValue<V>,
                S extends Store<S>,
                T extends BackwardTransferFunction<V, S>>
        extends Analysis<V, S, T> {

    /**
     * Get the output store at the entry block of a given CFG. Since the entry block is a {@link
     * SpecialBlock}, for a backward analysis, the output store should contains the analyzed flow
     * information from exit blocks to entry block.
     *
     * @return the output store at the entry block of a given CFG
     */
    @Nullable S getEntryStore();
}
