package org.checkerframework.dataflow.livevariable;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.analysis.AbstractValue;
import org.checkerframework.dataflow.cfg.node.Node;

/**
 * An implementation of an abstract value using for live variable analysis. A value holds the
 * information of a live variable. To run live variable analysis, see {@link
 * org.checkerframework.dataflow.cfg.playground.LiveVariablePlayground}.
 */
public class LiveVar implements AbstractValue<LiveVar> {

    /**
     * A live variable is represented by a node, which can be a {@link
     * org.checkerframework.dataflow.cfg.node.LocalVariableNode} or {@link
     * org.checkerframework.dataflow.cfg.node.FieldAccessNode}.
     */
    protected final Node liveVariable;

    @Override
    public LiveVar leastUpperBound(LiveVar other) {
        throw new RuntimeException("lub of LiveVar get called!");
    }

    /**
     * Create a new live variable.
     *
     * @param n a node
     */
    public LiveVar(Node n) {
        this.liveVariable = n;
    }

    @Override
    public int hashCode() {
        return this.liveVariable.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof LiveVar)) {
            return false;
        }
        LiveVar other = (LiveVar) obj;
        return this.liveVariable.equals(other.liveVariable);
    }

    @Override
    public String toString() {
        return this.liveVariable.toString();
    }
}
