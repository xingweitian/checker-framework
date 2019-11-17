package org.checkerframework.dataflow.cfg;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePathScanner;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.checkerframework.javacutil.BasicTypeProcessor;
import org.checkerframework.javacutil.TreeUtils;

/** The CFG processor. */
@SupportedAnnotationTypes("*")
public class CFGProcessor extends BasicTypeProcessor {

    private final String className;
    private final String methodName;

    private CompilationUnitTree rootTree;
    private ClassTree classTree;
    private MethodTree methodTree;

    private CFGProcessResult result;

    /** Class constructor. */
    protected CFGProcessor(String className, String methodName) {
        this.className = className;
        this.methodName = methodName;
        this.result = null;
    }

    /** Get the CFG process result. */
    public final CFGProcessResult getCFGProcessResult() {
        return this.result;
    }

    @Override
    public void typeProcessingOver() {
        if (rootTree == null) {
            this.result = new CFGProcessResult(null, false, "root tree is null.");
            return;
        }

        if (classTree == null) {
            this.result = new CFGProcessResult(null, false, "method tree is null.");
            return;
        }

        if (methodTree == null) {
            this.result = new CFGProcessResult(null, false, "class tree is null.");
            return;
        }

        ControlFlowGraph cfg = CFGBuilder.build(rootTree, methodTree, classTree, processingEnv);
        this.result = new CFGProcessResult(cfg);
    }

    @Override
    protected TreePathScanner<?, ?> createTreePathScanner(CompilationUnitTree root) {
        rootTree = root;
        return new TreePathScanner<Void, Void>() {
            @Override
            public Void visitClass(ClassTree node, Void p) {
                TypeElement el = TreeUtils.elementFromDeclaration(node);
                if (el.getSimpleName().contentEquals(className)) {
                    classTree = node;
                }
                return super.visitClass(node, p);
            }

            @Override
            public Void visitMethod(MethodTree node, Void p) {
                ExecutableElement el = TreeUtils.elementFromDeclaration(node);
                if (el.getSimpleName().contentEquals(methodName)) {
                    methodTree = node;
                    // stop execution by throwing an exception. this
                    // makes sure that compilation does not proceed, and
                    // thus the AST is not modified by further phases of
                    // the compilation (and we save the work to do the
                    // compilation).
                    throw new RuntimeException();
                }
                return null;
            }
        };
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /** The result of CFG process. */
    public static class CFGProcessResult {
        private final ControlFlowGraph controlFlowGraph;
        private final boolean isSuccess;
        private final String errMsg;

        /** Class constructor. */
        CFGProcessResult(final ControlFlowGraph cfg) {
            this(cfg, true, null);
            assert cfg != null : "this constructor should called if cfg were success built.";
        }

        /** Class constructor. */
        CFGProcessResult(ControlFlowGraph cfg, boolean isSuccess, String errMsg) {
            this.controlFlowGraph = cfg;
            this.isSuccess = isSuccess;
            this.errMsg = errMsg;
        }

        /** Check if the CFG process result is success. */
        public boolean isSuccess() {
            return isSuccess;
        }

        /** Get the generated control flow graph. */
        public ControlFlowGraph getCFG() {
            return controlFlowGraph;
        }

        /** Get the error message. */
        public String getErrMsg() {
            return errMsg;
        }
    }
}
