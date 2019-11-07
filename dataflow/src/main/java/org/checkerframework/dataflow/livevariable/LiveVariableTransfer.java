package org.checkerframework.dataflow.livevariable;

import java.util.List;
import org.checkerframework.dataflow.analysis.BackwardTransferFunction;
import org.checkerframework.dataflow.analysis.RegularTransferResult;
import org.checkerframework.dataflow.analysis.TransferInput;
import org.checkerframework.dataflow.analysis.TransferResult;
import org.checkerframework.dataflow.cfg.UnderlyingAST;
import org.checkerframework.dataflow.cfg.node.AbstractNodeVisitor;
import org.checkerframework.dataflow.cfg.node.AssignmentNode;
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.ObjectCreationNode;
import org.checkerframework.dataflow.cfg.node.ReturnNode;
import org.checkerframework.dataflow.cfg.node.StringConcatenateAssignmentNode;

public class LiveVariableTransfer
        extends AbstractNodeVisitor<
                TransferResult<LiveVar, LiveVariableStore>,
                TransferInput<LiveVar, LiveVariableStore>>
        implements BackwardTransferFunction<LiveVar, LiveVariableStore> {

    @Override
    public LiveVariableStore initialNormalExitStore(
            UnderlyingAST underlyingAST, List<ReturnNode> returnNodes) {
        return new LiveVariableStore();
    }

    @Override
    public LiveVariableStore initialExceptionalExitStore(UnderlyingAST underlyingAST) {
        return new LiveVariableStore();
    }

    @Override
    public RegularTransferResult<LiveVar, LiveVariableStore> visitNode(
            Node n, TransferInput<LiveVar, LiveVariableStore> p) {
        return new RegularTransferResult<>(null, p.getRegularStore().copy());
    }

    @Override
    public RegularTransferResult<LiveVar, LiveVariableStore> visitAssignment(
            AssignmentNode n, TransferInput<LiveVar, LiveVariableStore> p) {
        RegularTransferResult<LiveVar, LiveVariableStore> transferResult =
                (RegularTransferResult<LiveVar, LiveVariableStore>) super.visitAssignment(n, p);
        processLiveVarInAssignment(
                n.getTarget(), n.getExpression(), transferResult.getRegularStore());
        return transferResult;
    }

    @Override
    public RegularTransferResult<LiveVar, LiveVariableStore> visitStringConcatenateAssignment(
            StringConcatenateAssignmentNode n, TransferInput<LiveVar, LiveVariableStore> p) {
        RegularTransferResult<LiveVar, LiveVariableStore> transferResult =
                (RegularTransferResult<LiveVar, LiveVariableStore>)
                        super.visitStringConcatenateAssignment(n, p);
        processLiveVarInAssignment(
                n.getLeftOperand(), n.getRightOperand(), transferResult.getRegularStore());
        return transferResult;
    }

    @Override
    public RegularTransferResult<LiveVar, LiveVariableStore> visitMethodInvocation(
            MethodInvocationNode n, TransferInput<LiveVar, LiveVariableStore> p) {
        RegularTransferResult<LiveVar, LiveVariableStore> transferResult =
                (RegularTransferResult<LiveVar, LiveVariableStore>)
                        super.visitMethodInvocation(n, p);
        LiveVariableStore store = transferResult.getRegularStore();
        for (Node arg : n.getArguments()) {
            store.addUseInExpression(arg);
        }
        return transferResult;
    }

    @Override
    public RegularTransferResult<LiveVar, LiveVariableStore> visitObjectCreation(
            ObjectCreationNode n, TransferInput<LiveVar, LiveVariableStore> p) {
        RegularTransferResult<LiveVar, LiveVariableStore> transferResult =
                (RegularTransferResult<LiveVar, LiveVariableStore>) super.visitObjectCreation(n, p);
        LiveVariableStore store = transferResult.getRegularStore();
        for (Node arg : n.getArguments()) {
            store.addUseInExpression(arg);
        }
        return transferResult;
    }

    private void processLiveVarInAssignment(
            Node variable, Node expression, LiveVariableStore store) {
        store.killLiveVar(new LiveVar(variable));
        store.addUseInExpression(expression);
    }
}
