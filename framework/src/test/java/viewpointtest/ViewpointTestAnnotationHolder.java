package viewpointtest;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.Elements;
import org.checkerframework.framework.source.SourceChecker;
import org.checkerframework.javacutil.AnnotationBuilder;
import viewpointtest.quals.A;
import viewpointtest.quals.B;
import viewpointtest.quals.Bottom;
import viewpointtest.quals.PolyVP;
import viewpointtest.quals.ReceiverDependantQual;
import viewpointtest.quals.Top;

public class ViewpointTestAnnotationHolder {
    /* package-private */ static AnnotationMirror A, B, BOTTOM, POLYVP, RECEIVERDEPENDANTQUAL, TOP;

    public static void init(SourceChecker checker) {
        Elements elements = checker.getElementUtils();
        A = AnnotationBuilder.fromClass(elements, viewpointtest.quals.A.class);
        B = AnnotationBuilder.fromClass(elements, viewpointtest.quals.B.class);
        BOTTOM = AnnotationBuilder.fromClass(elements, Bottom.class);
        POLYVP = AnnotationBuilder.fromClass(elements, PolyVP.class);
        RECEIVERDEPENDANTQUAL = AnnotationBuilder.fromClass(elements, ReceiverDependantQual.class);
        TOP = AnnotationBuilder.fromClass(elements, Top.class);
    }
}
