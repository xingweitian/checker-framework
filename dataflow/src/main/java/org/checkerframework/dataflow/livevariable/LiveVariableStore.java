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

public class LiveVariableStore implements Store<LiveVariableStore> {

    /** A set of live variables. */
    private Set<LiveVar> liveVarSet;

    /** The class constructor of LiveVariableStore. */
    public LiveVariableStore() {
        liveVarSet = new HashSet<>();
    }

    /**
     * The class constructor of LiveVariableStore.
     *
     * @param liveVarSet a set of LiveVar
     */
    public LiveVariableStore(Set<LiveVar> liveVarSet) {
        this.liveVarSet = liveVarSet;
    }

    /**
     * Put {@code variable} into {@code liveVarSet}.
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

        if (!(obj instanceof LiveVariableStore)) {
            return false;
        }

        LiveVariableStore other = (LiveVariableStore) obj;
        return other.liveVarSet.equals(this.liveVarSet);
    }

    @Override
    public int hashCode() {
        return this.liveVarSet.hashCode();
    }

    @Override
    public LiveVariableStore copy() {
        Set<LiveVar> liveVarSetCopy = new HashSet<>();
        liveVarSetCopy.addAll(liveVarSet);
        return new LiveVariableStore(liveVarSetCopy);
    }

    @Override
    public LiveVariableStore leastUpperBound(LiveVariableStore other) {
        Set<LiveVar> liveVarSetLub = new HashSet<>();
        liveVarSetLub.addAll(this.liveVarSet);
        liveVarSetLub.addAll(other.liveVarSet);
        return new LiveVariableStore(liveVarSetLub);
    }

    /** It is not used by backward analysis, so just return null. */
    @Override
    public LiveVariableStore widenedUpperBound(LiveVariableStore previous) {
        return null;
    }

    @Override
    public boolean canAlias(Receiver a, Receiver b) {
        return true;
    }

    @Override
    public String visualize(CFGVisualizer<?, LiveVariableStore, ?> viz) {
        StringBuilder sbStoreVal = new StringBuilder();
        if (liveVarSet.isEmpty()) {
            sbStoreVal.append("null");
            return sbStoreVal.toString();
        }
        for (LiveVar liveVar : liveVarSet) {
            sbStoreVal.append(viz.visualizeStoreVal(liveVar.liveNode));
        }
        return sbStoreVal.toString();
    }

    @Override
    public String toString() {
        return liveVarSet.toString();
    }
}
