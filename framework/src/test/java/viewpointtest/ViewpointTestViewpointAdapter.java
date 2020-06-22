package viewpointtest;

import static viewpointtest.ViewpointTestAnnotationHolder.RECEIVERDEPENDANTQUAL;
import static viewpointtest.ViewpointTestAnnotationHolder.TOP;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeKind;
import org.checkerframework.framework.type.AbstractViewpointAdapter;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.javacutil.AnnotationUtils;

public class ViewpointTestViewpointAdapter extends AbstractViewpointAdapter {

    /**
     * The class constructor.
     *
     * @param atypeFactory
     */
    public ViewpointTestViewpointAdapter(AnnotatedTypeFactory atypeFactory) {
        super(atypeFactory);
    }

    @Override
    protected AnnotationMirror extractAnnotationMirror(AnnotatedTypeMirror atm) {
        return atm.getAnnotationInHierarchy(TOP);
    }

    @Override
    protected AnnotatedTypeMirror combineTypeWithType(
            AnnotatedTypeMirror receiver, AnnotatedTypeMirror declared) {
        // skip method decl
        if (declared.getKind() == TypeKind.EXECUTABLE) {
            return declared;
        }
        return super.combineTypeWithType(receiver, declared);
    }

    @Override
    protected AnnotationMirror combineAnnotationWithAnnotation(
            AnnotationMirror receiverAnnotation, AnnotationMirror declaredAnnotation) {

        if (AnnotationUtils.areSame(declaredAnnotation, RECEIVERDEPENDANTQUAL)) {
            return receiverAnnotation;
        }
        return declaredAnnotation;
    }
}
