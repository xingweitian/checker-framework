package typedecldefault;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;

import typedecldefault.quals.*;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TypeDeclDefaultAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {
    public TypeDeclDefaultAnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker);
        this.postInit();
    }

    @Override
    protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
        return new HashSet<>(
                Arrays.asList(
                        TypeDeclDefaultTop.class,
                        TypeDeclDefaultMiddle.class,
                        TypeDeclDefaultBottom.class,
                        PolyTypeDeclDefault.class));
    }
}
