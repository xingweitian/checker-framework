package typedecldefault.quals;

import org.checkerframework.framework.qual.DefaultFor;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeUseLocation;

import java.lang.annotation.*;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

/** This is the top qualifier of the TypeDeclDefault type system. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@SubtypeOf({})
@DefaultFor(TypeUseLocation.CONSTRUCTOR_RESULT)
public @interface TypeDeclDefaultTop {}
