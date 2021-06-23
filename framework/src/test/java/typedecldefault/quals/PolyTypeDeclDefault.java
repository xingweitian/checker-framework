package typedecldefault.quals;

import org.checkerframework.framework.qual.PolymorphicQualifier;

import java.lang.annotation.*;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

/** A polymorphic qualifier for the TyepDeclDefault type system. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@PolymorphicQualifier
public @interface PolyTypeDeclDefault {}
