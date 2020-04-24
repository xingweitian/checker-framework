package org.checkerframework.framework.type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import org.checkerframework.dataflow.qual.SideEffectFree;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedArrayType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedNullType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedWildcardType;
import org.checkerframework.javacutil.BugInCF;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.Pair;

/**
 * Abstract utility class for performing viewpoint adaptation.
 *
 * <p>This class contains the common logic for extracting and inserting viewpoint adapted
 * annotations into the corresponding types for member/field access, constructor and method
 * invocations, and type parameter bound instantiations.
 *
 * <p>Subclasses implement the computation of the precise viewpoint adapted type given a receiver
 * type and a declared type, and implement how to extract the qualifier given an ATM.
 */
public abstract class AbstractViewpointAdapter implements ViewpointAdapter {

    // This prevents calling combineTypeWithType on type variable if it is an upper bound
    // of another type variable. We only viewpoint adapt type variable that is not upper-bound.
    private boolean isTypeVarExtends = false;

    /** The annotated type factory. */
    protected final AnnotatedTypeFactory atypeFactory;

    /** The class constructor. */
    public AbstractViewpointAdapter(final AnnotatedTypeFactory atypeFactory) {
        this.atypeFactory = atypeFactory;
    }

    @Override
    public void viewpointAdaptMember(
            AnnotatedTypeMirror receiverType,
            Element memberElement,
            AnnotatedTypeMirror memberType) {
        if (!shouldAdaptMember(memberType, memberElement)) {
            return;
        }

        AnnotatedTypeMirror decltype = atypeFactory.getAnnotatedType(memberElement);
        AnnotatedTypeMirror combinedType = combineTypeWithType(receiverType, decltype);
        memberType.replaceAnnotations(combinedType.getAnnotations());
        if (memberType.getKind() == TypeKind.DECLARED
                && combinedType.getKind() == TypeKind.DECLARED) {
            AnnotatedDeclaredType adtType = (AnnotatedDeclaredType) memberType;
            AnnotatedDeclaredType adtCombinedType = (AnnotatedDeclaredType) combinedType;
            adtType.setTypeArguments(adtCombinedType.getTypeArguments());
        } else if (memberType.getKind() == TypeKind.ARRAY
                && combinedType.getKind() == TypeKind.ARRAY) {
            AnnotatedArrayType aatType = (AnnotatedArrayType) memberType;
            AnnotatedArrayType aatCombinedType = (AnnotatedArrayType) combinedType;
            aatType.setComponentType(aatCombinedType.getComponentType());
        }
    }

    /**
     * Determines whether a particular member should be viewpoint adapted or not. The default
     * implementation adapts all members except for local variables and method formal parameters.
     *
     * @param type type of the member, used to decide whether a member should be viewpoint adapted
     *     or not. A subclass of {@link ViewpointAdapter} may disable viewpoint adaptation for
     *     elements based on their types.
     * @param element element of the member
     * @return true if the member needs viewpoint adaptation
     */
    protected boolean shouldAdaptMember(AnnotatedTypeMirror type, Element element) {
        if (element.getKind() == ElementKind.LOCAL_VARIABLE
                || element.getKind() == ElementKind.PARAMETER) {
            return false;
        }
        return true;
    }

    @Override
    public void viewpointAdaptConstructor(
            AnnotatedTypeMirror receiverType,
            ExecutableElement constructorElt,
            AnnotatedExecutableType constructorType) {

        AnnotatedExecutableType declConstructorType = atypeFactory.getAnnotatedType(constructorElt);

        // For constructors, we adapt parameter types, return type and type parameters
        List<AnnotatedTypeMirror> parameterTypes = declConstructorType.getParameterTypes();
        List<AnnotatedTypeVariable> typeVariables = declConstructorType.getTypeVariables();
        AnnotatedTypeMirror constructorReturn = declConstructorType.getReturnType();

        Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mappings = new IdentityHashMap<>();
        for (AnnotatedTypeMirror parameterType : parameterTypes) {
            AnnotatedTypeMirror p = combineTypeWithType(receiverType, parameterType);
            mappings.put(parameterType, p);
        }
        for (AnnotatedTypeMirror typeVariable : typeVariables) {
            AnnotatedTypeMirror tv = combineTypeWithType(receiverType, typeVariable);
            mappings.put(typeVariable, tv);
        }
        AnnotatedTypeMirror cr = combineTypeWithType(receiverType, constructorReturn);
        mappings.put(constructorReturn, cr);

        declConstructorType =
                (AnnotatedExecutableType)
                        AnnotatedTypeCopierWithReplacement.replace(declConstructorType, mappings);

        constructorType.setParameterTypes(declConstructorType.getParameterTypes());
        constructorType.setTypeVariables(declConstructorType.getTypeVariables());
        constructorType.setReturnType(declConstructorType.getReturnType());
    }

    @Override
    public void viewpointAdaptMethod(
            AnnotatedTypeMirror receiverType,
            ExecutableElement methodElt,
            AnnotatedExecutableType methodType) {
        if (!shouldAdaptMethod(methodElt)) {
            return;
        }

        AnnotatedExecutableType declMethodType = atypeFactory.getAnnotatedType(methodElt);

        // For methods, we additionally adapt method receiver compared to constructors
        List<AnnotatedTypeMirror> parameterTypes = declMethodType.getParameterTypes();
        List<AnnotatedTypeVariable> typeVariables = declMethodType.getTypeVariables();
        AnnotatedTypeMirror returnType = declMethodType.getReturnType();
        AnnotatedTypeMirror methodReceiver = declMethodType.getReceiverType();

        Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mappings = new IdentityHashMap<>();

        for (AnnotatedTypeMirror parameterType : parameterTypes) {
            AnnotatedTypeMirror p = combineTypeWithType(receiverType, parameterType);
            mappings.put(parameterType, p);
        }

        for (AnnotatedTypeVariable typeVariable : typeVariables) {
            AnnotatedTypeMirror tv = combineTypeWithType(receiverType, typeVariable);
            mappings.put(typeVariable, tv);
        }

        if (returnType.getKind() != TypeKind.VOID) {
            AnnotatedTypeMirror r = combineTypeWithType(receiverType, returnType);
            mappings.put(returnType, r);
        }

        if (methodReceiver != null) {
            AnnotatedTypeMirror mr = combineTypeWithType(receiverType, methodReceiver);
            mappings.put(methodReceiver, mr);
        }

        declMethodType =
                (AnnotatedExecutableType)
                        AnnotatedTypeCopierWithReplacement.replace(declMethodType, mappings);

        // Because we can't viewpoint adapt asMemberOf result, we adapt the declared method first,
        // and sets the corresponding parts to asMemberOf result
        methodType.setReturnType(declMethodType.getReturnType());
        methodType.setReceiverType(declMethodType.getReceiverType());
        methodType.setParameterTypes(declMethodType.getParameterTypes());
        methodType.setTypeVariables(declMethodType.getTypeVariables());
    }

    /** Check if the method invocation should be adapted. */
    protected boolean shouldAdaptMethod(ExecutableElement element) {
        return !ElementUtils.isStatic(element);
    }

    @Override
    public void viewpointAdaptTypeParameterBounds(
            AnnotatedTypeMirror receiverType,
            List<AnnotatedTypeParameterBounds> typeParameterBounds) {

        List<AnnotatedTypeParameterBounds> adaptedTypeParameterBounds = new ArrayList<>();
        for (AnnotatedTypeParameterBounds typeParameterBound : typeParameterBounds) {
            AnnotatedTypeMirror adaptedUpper =
                    combineTypeWithType(receiverType, typeParameterBound.getUpperBound());
            AnnotatedTypeMirror adaptedLower =
                    combineTypeWithType(receiverType, typeParameterBound.getLowerBound());
            adaptedTypeParameterBounds.add(
                    new AnnotatedTypeParameterBounds(adaptedUpper, adaptedLower));
        }

        typeParameterBounds.clear();
        typeParameterBounds.addAll(adaptedTypeParameterBounds);
    }

    /**
     * Viewpoint adapt declared type to receiver type, and return the result atm
     *
     * @param receiver receiver type
     * @param declared declared type
     * @return {@link AnnotatedTypeMirror} after viewpoint adaptation
     */
    protected AnnotatedTypeMirror combineTypeWithType(
            AnnotatedTypeMirror receiver, AnnotatedTypeMirror declared) {
        assert receiver != null && declared != null;

        AnnotatedTypeMirror result = declared;

        if (receiver.getKind() == TypeKind.TYPEVAR) {
            receiver = ((AnnotatedTypeVariable) receiver).getUpperBound();
        }
        AnnotationMirror receiverAnnotation = extractAnnotationMirror(receiver);
        if (receiverAnnotation != null) {
            result = combineAnnotationWithType(receiverAnnotation, declared);
            result = substituteTVars(receiver, result);
        }

        return result;
    }

    /**
     * Extract qualifier from {@link AnnotatedTypeMirror}.
     *
     * @param atm AnnotatedTypeMirror from which qualifier is extracted
     * @return qualifier extracted
     */
    protected abstract AnnotationMirror extractAnnotationMirror(AnnotatedTypeMirror atm);

    /**
     * Sub-procedure to combine receiver qualifiers with declared types. qualifiers are extracted
     * from declared types to furthur perform viewpoint adaptation only between two qualifiers.
     *
     * @param receiverAnnotation receiver qualifier
     * @param declared declared type
     * @return {@link AnnotatedTypeMirror} after viewpoint adaptation
     */
    protected AnnotatedTypeMirror combineAnnotationWithType(
            AnnotationMirror receiverAnnotation, AnnotatedTypeMirror declared) {
        if (declared.getKind().isPrimitive()) {
            AnnotatedPrimitiveType apt = (AnnotatedPrimitiveType) declared.shallowCopy();

            AnnotationMirror resultAnnotation =
                    combineAnnotationWithAnnotation(
                            receiverAnnotation, extractAnnotationMirror(apt));
            apt.replaceAnnotation(resultAnnotation);
            return apt;
        } else if (declared.getKind() == TypeKind.TYPEVAR) {
            if (!isTypeVarExtends) {
                isTypeVarExtends = true;
                AnnotatedTypeVariable atv = (AnnotatedTypeVariable) declared.shallowCopy();
                Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mapping = new HashMap<>();

                // For type variables, we recursively adapt upper and lower bounds
                AnnotatedTypeMirror resUpper =
                        combineAnnotationWithType(receiverAnnotation, atv.getUpperBound());
                mapping.put(atv.getUpperBound(), resUpper);

                AnnotatedTypeMirror resLower =
                        combineAnnotationWithType(receiverAnnotation, atv.getLowerBound());
                mapping.put(atv.getLowerBound(), resLower);

                AnnotatedTypeMirror result =
                        AnnotatedTypeCopierWithReplacement.replace(atv, mapping);

                isTypeVarExtends = false;
                return result;
            }
            return declared;
        } else if (declared.getKind() == TypeKind.DECLARED) {
            AnnotatedDeclaredType adt = (AnnotatedDeclaredType) declared.shallowCopy();

            // Mapping between declared type argument to combined type argument
            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mapping = new HashMap<>();

            AnnotationMirror resultAnnotation =
                    combineAnnotationWithAnnotation(
                            receiverAnnotation, extractAnnotationMirror(adt));

            // Recursively combine type arguments and store to map
            for (AnnotatedTypeMirror typeArgument : adt.getTypeArguments()) {
                // Recursively adapt the type arguments of this adt
                AnnotatedTypeMirror combinedTypeArgument =
                        combineAnnotationWithType(receiverAnnotation, typeArgument);
                mapping.put(typeArgument, combinedTypeArgument);
            }

            // Construct result type
            AnnotatedTypeMirror result = AnnotatedTypeCopierWithReplacement.replace(adt, mapping);
            result.replaceAnnotation(resultAnnotation);

            return result;
        } else if (declared.getKind() == TypeKind.ARRAY) {
            AnnotatedArrayType aat = (AnnotatedArrayType) declared.shallowCopy();

            // Replace the main qualifier
            AnnotationMirror resultAnnotation =
                    combineAnnotationWithAnnotation(
                            receiverAnnotation, extractAnnotationMirror(aat));
            aat.replaceAnnotation(resultAnnotation);

            // Combine component type recursively and sets combined component type
            AnnotatedTypeMirror compo = aat.getComponentType();
            // Recursively call itself first on the component type
            AnnotatedTypeMirror combinedCompoType =
                    combineAnnotationWithType(receiverAnnotation, compo);
            aat.setComponentType(combinedCompoType);

            return aat;
        } else if (declared.getKind() == TypeKind.WILDCARD) {
            AnnotatedWildcardType awt = (AnnotatedWildcardType) declared.shallowCopy();

            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mapping = new HashMap<>();

            // There is no main qualifier for a wildcard

            // Adapt extend
            AnnotatedTypeMirror extend = awt.getExtendsBound();
            if (extend != null) {
                // Recursively adapt the extends bound of this awt
                AnnotatedTypeMirror combinedExtend =
                        combineAnnotationWithType(receiverAnnotation, extend);
                mapping.put(extend, combinedExtend);
            }

            // Adapt super
            AnnotatedTypeMirror zuper = awt.getSuperBound();
            if (zuper != null) {
                // Recursively adapt the lower bound of this awt
                AnnotatedTypeMirror combinedZuper =
                        combineAnnotationWithType(receiverAnnotation, zuper);
                mapping.put(zuper, combinedZuper);
            }

            AnnotatedTypeMirror result = AnnotatedTypeCopierWithReplacement.replace(awt, mapping);

            return result;
        } else if (declared.getKind() == TypeKind.NULL) {
            AnnotatedNullType ant = (AnnotatedNullType) declared.shallowCopy(true);
            AnnotationMirror resultAnnotation =
                    combineAnnotationWithAnnotation(
                            receiverAnnotation, extractAnnotationMirror(ant));
            ant.replaceAnnotation(resultAnnotation);
            return ant;
        } else {
            throw new BugInCF(
                    "ViewpointAdaptor::combineAnnotationWithType: Unknown decl: "
                            + declared
                            + " of kind: "
                            + declared.getKind());
        }
    }

    /**
     * Viewpoint adapt declared qualifier to receiver qualifier.
     *
     * @param receiverAnnotation receiver qualifier
     * @param declaredAnnotation declared qualifier
     * @return result qualifier after viewpoint adaptation
     */
    @SideEffectFree
    protected abstract AnnotationMirror combineAnnotationWithAnnotation(
            AnnotationMirror receiverAnnotation, AnnotationMirror declaredAnnotation);

    /**
     * If rhs contains/is type variable use whose type arguments should be inferred from the
     * receiver, i.e. lhs, this method substitutes that type argument into rhs, and return the
     * reference to rhs. This method is side effect free, because rhs will be copied and that copy
     * gets modified and returned.
     *
     * @param lhs type from which type arguments are extracted to replace formal type parameters of
     *     rhs.
     * @param rhs AnnotatedTypeMirror that might be a formal type parameter
     * @return rhs' copy with its type parameter substituted
     */
    private AnnotatedTypeMirror substituteTVars(AnnotatedTypeMirror lhs, AnnotatedTypeMirror rhs) {
        if (rhs.getKind() == TypeKind.TYPEVAR) {
            AnnotatedTypeVariable atv = (AnnotatedTypeVariable) rhs.shallowCopy();

            // Base case where actual type argument is extracted
            if (lhs.getKind() == TypeKind.DECLARED) {
                rhs = getTypeVariableSubstitution((AnnotatedDeclaredType) lhs, atv);
            }
        } else if (rhs.getKind() == TypeKind.DECLARED) {
            AnnotatedDeclaredType adt = (AnnotatedDeclaredType) rhs.shallowCopy();
            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mapping = new HashMap<>();

            for (AnnotatedTypeMirror formalTypeParameter : adt.getTypeArguments()) {
                AnnotatedTypeMirror actualTypeArgument = substituteTVars(lhs, formalTypeParameter);
                mapping.put(formalTypeParameter, actualTypeArgument);
                // The following code does the wrong thing!
            }
            // We must use AnnotatedTypeReplacer to replace the formal type parameters with actual
            // type arguments, but not replace with its main qualifier
            rhs = AnnotatedTypeCopierWithReplacement.replace(adt, mapping);
        } else if (rhs.getKind() == TypeKind.WILDCARD) {
            AnnotatedWildcardType awt = (AnnotatedWildcardType) rhs.shallowCopy();
            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mapping = new HashMap<>();

            AnnotatedTypeMirror extend = awt.getExtendsBound();
            if (extend != null) {
                AnnotatedTypeMirror substExtend = substituteTVars(lhs, extend);
                mapping.put(extend, substExtend);
            }

            AnnotatedTypeMirror zuper = awt.getSuperBound();
            if (zuper != null) {
                AnnotatedTypeMirror substZuper = substituteTVars(lhs, zuper);
                mapping.put(zuper, substZuper);
            }

            rhs = AnnotatedTypeCopierWithReplacement.replace(awt, mapping);
        } else if (rhs.getKind() == TypeKind.ARRAY) {
            AnnotatedArrayType aat = (AnnotatedArrayType) rhs.shallowCopy();
            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mapping = new HashMap<>();

            AnnotatedTypeMirror compnentType = aat.getComponentType();
            // Type variable of compnentType already gets substituted
            AnnotatedTypeMirror substCompnentType = substituteTVars(lhs, compnentType);
            mapping.put(compnentType, substCompnentType);

            // Construct result type
            rhs = AnnotatedTypeCopierWithReplacement.replace(aat, mapping);
        } else if (rhs.getKind().isPrimitive() || rhs.getKind() == TypeKind.NULL) {
            // nothing to do for primitive types and the null type
        } else {
            throw new BugInCF(
                    "ViewpointAdaptor::substituteTVars: Cannot handle rhs: "
                            + rhs
                            + " of kind: "
                            + rhs.getKind());
        }

        return rhs;
    }

    /**
     * Return actual type argument for formal type parameter "var" from 'type"
     *
     * @param type type from which type arguments are extracted to replace "var"
     * @param var formal type parameter that needs real type arguments
     * @return Real type argument
     */
    private AnnotatedTypeMirror getTypeVariableSubstitution(
            AnnotatedDeclaredType type, AnnotatedTypeVariable var) {
        Pair<AnnotatedDeclaredType, Integer> res = findDeclType(type, var);

        if (res == null) {
            return var;
        }

        AnnotatedDeclaredType decltype = res.first;
        int foundindex = res.second;

        List<AnnotatedTypeMirror> tas = decltype.getTypeArguments();
        // return a copy, as we want to modify the type later.
        return tas.get(foundindex).shallowCopy(true);
    }

    /**
     * Find the index(position) of this type variable from type
     *
     * @param type type from which we infer actual type arguments
     * @param var formal type parameter
     * @return index(position) of this type variable from type
     */
    private Pair<AnnotatedDeclaredType, Integer> findDeclType(
            AnnotatedDeclaredType type, AnnotatedTypeVariable var) {
        Element varelem = var.getUnderlyingType().asElement();

        DeclaredType dtype = type.getUnderlyingType();
        TypeElement el = (TypeElement) dtype.asElement();
        List<? extends TypeParameterElement> tparams = el.getTypeParameters();
        int foundindex = 0;

        for (TypeParameterElement tparam : tparams) {
            if (tparam.equals(varelem)) {
                // we found the right index!
                break;
            }
            ++foundindex;
        }

        if (foundindex >= tparams.size()) {
            // Didn't find the desired type => Head for super type of "type"!
            for (AnnotatedDeclaredType sup : type.directSuperTypes()) {
                Pair<AnnotatedDeclaredType, Integer> res = findDeclType(sup, var);
                if (res != null) {
                    return res;
                }
            }
            // We reach this point if the variable wasn't found in any recursive call on ALL direct
            // supertypes.
            return null;
        }

        return Pair.of(type, foundindex);
    }
}
