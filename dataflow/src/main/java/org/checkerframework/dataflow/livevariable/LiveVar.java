package org.checkerframework.dataflow.livevariable;

import org.checkerframework.dataflow.analysis.AbstractValue;
import org.checkerframework.dataflow.cfg.node.Node;

/** The live variable class. */
public class LiveVar implements AbstractValue<LiveVar> {

    /** The live node. */
    Node liveNode;

    @Override
    public LiveVar leastUpperBound(LiveVar other) {
        throw new RuntimeException("lub of LiveVar get called!");
    }

    /**
     * The class constructor of LiveVar.
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
    public boolean equals(Object obj) {
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
