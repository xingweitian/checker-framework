package org.checkerframework.dataflow.livevariable;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.analysis.AbstractValue;
import org.checkerframework.dataflow.cfg.node.Node;

/** The live variable class. */
public class LiveVar implements AbstractValue<LiveVar> {

    /**
     * The live node which can be a {@link org.checkerframework.dataflow.cfg.node.LocalVariableNode}
     * or {@link org.checkerframework.dataflow.cfg.node.FieldAccessNode}.
     */
    protected final Node liveNode;

    @Override
    public LiveVar leastUpperBound(LiveVar other) {
        throw new RuntimeException("lub of LiveVar get called!");
    }

    /**
     * Create a new live variable for {@code n}.
     *
     * @param n a Node
     */
    public LiveVar(Node n) {
        this.liveNode = n;
    }

    @Override
    public int hashCode() {
        return this.liveNode.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof LiveVar)) {
            return false;
        }

        LiveVar other = (LiveVar) obj;
        return this.liveNode.equals(other.liveNode);
    }

    @Override
    public String toString() {
        return this.liveNode.toString();
    }
}
