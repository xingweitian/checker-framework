package org.checkerframework.dataflow.livevariable;

import java.util.HashSet;
import java.util.Set;
import org.checkerframework.dataflow.analysis.FlowExpressions.Receiver;
import org.checkerframework.dataflow.analysis.Store;
import org.checkerframework.dataflow.cfg.CFGVisualizer;
import org.checkerframework.dataflow.cfg.node.BinaryOperationNode;
import org.checkerframework.dataflow.cfg.node.FieldAccessNode;
import org.checkerframework.dataflow.cfg.node.InstanceOfNode;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.TernaryExpressionNode;
import org.checkerframework.dataflow.cfg.node.TypeCastNode;
import org.checkerframework.dataflow.cfg.node.UnaryOperationNode;

/** The live variable store class. */
public class LiveVarStore implements Store<LiveVarStore> {

    /** A set of live variables. */
    private final Set<LiveVar> liveVarSet;

    /** Create a new LiveVarStore. */
    public LiveVarStore() {
        liveVarSet = new HashSet<>();
    }

    /**
     * Create a new LiveVarStore.
     *
     * @param liveVarSet a set of LiveVar
     */
    public LiveVarStore(Set<LiveVar> liveVarSet) {
        this.liveVarSet = liveVarSet;
    }

    /**
     * Add {@code variable} into {@code liveVarSet}.
     *
     * @param variable a LiveVar
     */
    public void putLiveVar(LiveVar variable) {
        liveVarSet.add(variable);
    }

    /**
     * Remove {@code variable} from {@code liveVarSet}.
     *
     * @param variable a LiveVar
     */
    public void killLiveVar(LiveVar variable) {
        liveVarSet.remove(variable);
    }

    /**
     * Add the LiveVars in expression to liveVarSet.
     *
     * @param expression a Node
     */
    public void addUseInExpression(Node expression) {
        if (expression instanceof BinaryOperationNode) {
            BinaryOperationNode binaryNode = (BinaryOperationNode) expression;
            addUseInExpression(binaryNode.getLeftOperand());
            addUseInExpression(binaryNode.getRightOperand());
        } else if (expression instanceof UnaryOperationNode) {
            UnaryOperationNode unaryNode = (UnaryOperationNode) expression;
            addUseInExpression(unaryNode.getOperand());
        } else if (expression instanceof TernaryExpressionNode) {
            TernaryExpressionNode ternaryNode = (TernaryExpressionNode) expression;
            addUseInExpression(ternaryNode.getConditionOperand());
            addUseInExpression(ternaryNode.getThenOperand());
            addUseInExpression(ternaryNode.getElseOperand());
        } else if (expression instanceof TypeCastNode) {
            TypeCastNode typeCastNode = (TypeCastNode) expression;
            addUseInExpression(typeCastNode.getOperand());
        } else if (expression instanceof InstanceOfNode) {
            InstanceOfNode instanceOfNode = (InstanceOfNode) expression;
            addUseInExpression(instanceOfNode.getOperand());
        } else if (expression instanceof LocalVariableNode
                || expression instanceof FieldAccessNode) {
            LiveVar liveVar = new LiveVar(expression);
            putLiveVar(liveVar);
        }
    }

    @Override
    public boolean equals(Object obj) {

        if (!(obj instanceof LiveVarStore)) {
            return false;
        }

        LiveVarStore other = (LiveVarStore) obj;
        return other.liveVarSet.equals(this.liveVarSet);
    }

    @Override
    public int hashCode() {
        return this.liveVarSet.hashCode();
    }

    @Override
    public LiveVarStore copy() {
        Set<LiveVar> liveVarSetCopy = new HashSet<>(liveVarSet);
        return new LiveVarStore(liveVarSetCopy);
    }

    @Override
    public LiveVarStore leastUpperBound(LiveVarStore other) {
        Set<LiveVar> liveVarSetLub = new HashSet<>();
        liveVarSetLub.addAll(this.liveVarSet);
        liveVarSetLub.addAll(other.liveVarSet);
        return new LiveVarStore(liveVarSetLub);
    }

    /** It is not used by backward analysis, so just return null. */
    @Override
    public LiveVarStore widenedUpperBound(LiveVarStore previous) {
        return null;
    }

    @Override
    public boolean canAlias(Receiver a, Receiver b) {
        return true;
    }

    @Override
    public String visualize(CFGVisualizer<?, LiveVarStore, ?> viz) {
        if (liveVarSet.isEmpty()) {
            return "No live variables.";
        }

        StringBuilder sbStoreVal = new StringBuilder();

        for (LiveVar liveVar : liveVarSet) {
            sbStoreVal.append(viz.visualizeLiveVarStoreVal(liveVar.liveNode));
        }

        return sbStoreVal.toString();
    }

    @Override
    public String toString() {
        return liveVarSet.toString();
    }
}
