package org.checkerframework.dataflow.analysis;

import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.cfg.UnderlyingAST;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.dataflow.cfg.node.Node;

/**
 * General interface of a forward transfer function for the abstract interpretation used for the
 * forward flow analysis.
 *
 * <p>A forward transfer function consists of the following components:
 *
 * <ul>
 *   <li>A method {@code initialStore} that determines which initial store should be used in the
 *       org.checkerframework.dataflow forward analysis.
 *   <li>A function for every {@link Node} type that determines the behavior of the
 *       org.checkerframework.dataflow analysis in that case. This method takes a {@link Node} and
 *       an incoming store, and produces a {@link RegularTransferResult}.
 * </ul>
 *
 * <p><em>Important</em>: The individual transfer functions ( {@code visit*}) are allowed to use
 * (and modify) the stores contained in the argument passed; the ownership is transferred from the
 * caller to that function.
 *
 * @param <V> the abstract value type to be tracked by the analysis
 * @param <S> the store type used in the analysis
 */
public interface ForwardTransferFunction<V extends AbstractValue<V>, S extends Store<S>>
        extends TransferFunction<V, S> {

    /**
     * Return the initial store to be used by the org.checkerframework.dataflow analysis. {@code
     * parameters} is only set if the underlying AST is a method.
     *
     * @param underlyingAST an abstract syntax tree
     * @param parameters a list of local variable nodes
     * @return the initial store
     */
    S initialStore(UnderlyingAST underlyingAST, @Nullable List<LocalVariableNode> parameters);
}
