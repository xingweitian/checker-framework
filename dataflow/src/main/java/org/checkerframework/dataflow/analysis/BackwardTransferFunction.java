package org.checkerframework.dataflow.analysis;

import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.cfg.UnderlyingAST;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.ReturnNode;

/**
 * General interface of a backward transfer function for the abstract interpretation used for the
 * backward flow analysis.
 *
 * <p>A backward transfer function consists of the following components:
 *
 * <ul>
 *   <li>A method {@code initialNormalExitStore} that determines which initial store should be used
 *       at the normal exit block in the org.checkerframework.dataflow backward analysis.
 *   <li>A method {@code initialExceptionalExitStore} that determines which initial store should be
 *       used at the exceptional exit block in the org.checkerframework.dataflow backward analysis.
 *   <li>A function for every {@link Node} type that determines the behavior of the
 *       org.checkerframework.dataflow analysis in that case. This method takes a {@link Node} and
 *       an incoming store, and produces a {@link RegularTransferResult}.
 * </ul>
 *
 * <p><em>Important</em>: The individual transfer functions ( {@code visit*}) are allowed to use
 * (and modify) the stores contained in the argument passed; the ownership is transfered from the
 * caller to that function.
 *
 * @param <V> the abstract value type to be tracked by the analysis
 * @param <S> the store type used in the analysis
 */
public interface BackwardTransferFunction<V extends AbstractValue<V>, S extends Store<S>>
        extends TransferFunction<V, S> {

    /**
     * Return the initial store that should be used at the normal exit block.
     *
     * @param underlyingAST the underlying AST of the given control flow graph
     * @param returnNodes the return nodes of the given control flow graph if the underlying AST of
     *     this graph is a method. Otherwise will be set to null
     * @return the initial store that should be used at the normal exit block
     */
    S initialNormalExitStore(UnderlyingAST underlyingAST, @Nullable List<ReturnNode> returnNodes);

    /**
     * Return the initial store that should be used at the exceptional exit block or given the
     * underlying AST of a control flow graph.
     *
     * @param underlyingAST the underlying AST of the given control flow graph
     * @return the initial store that should be used at the exceptional exit block
     */
    S initialExceptionalExitStore(UnderlyingAST underlyingAST);
}
