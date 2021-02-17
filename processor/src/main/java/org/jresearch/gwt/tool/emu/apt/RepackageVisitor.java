package org.jresearch.gwt.tool.emu.apt;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor8;

import one.util.streamex.StreamEx;

@SuppressWarnings("resource")
public class RepackageVisitor extends SimpleTypeVisitor8<TypeMirror, State> {

	private ProcessingEnvironment env;
	private String emuPackage;

	public RepackageVisitor(ProcessingEnvironment env, String emuPackage) {
		this.env = env;
		this.emuPackage = emuPackage;
	}

	@Override
	protected TypeMirror defaultAction(TypeMirror e, State state) {
		return e;
	}

//	@Override
//	public TypeMirror visitIntersection(IntersectionType t, State state) {
//		State interceptionState = new State();
//		StreamEx.of(t.getBounds())
//				.map(type -> type.accept(this, interceptionState))
//				.toArray(TypeMirror.class);
//		if (interceptionState.isChanged()) {
////			report error
//		}
//		return t;
//	}

//	@Override
//	public TypeMirror visitTypeVariable(TypeVariable t, State state) {
//		State lowerBoundState = new State();
//		TypeMirror lowerBound = t.getLowerBound().accept(this, lowerBoundState);
//		State upperBoundState = new State();
//		TypeMirror upperBound = t.getUpperBound().accept(this, upperBoundState);
//		if (lowerBoundState.isChanged() || upperBoundState.isChanged()) {
////			report error
//		}
//		return t;
//	}

//	@Override
//	public TypeMirror visitUnion(UnionType t, State state) {
//		State unionState = new State();
//		StreamEx.of(t.getAlternatives())
//				.map(type -> type.accept(this, unionState))
//				.toArray(TypeMirror.class);
//		if (unionState.isChanged()) {
////			report error
//		}
//		return t;
//	}

	@Override
	public TypeMirror visitWildcard(WildcardType t, State state) {
		TypeMirror extendsBound = t.getExtendsBound().accept(this, state);
		TypeMirror superBound = t.getSuperBound().accept(this, state);
		return env.getTypeUtils().getWildcardType(extendsBound, superBound);
	}

	@Override
	public TypeMirror visitArray(ArrayType t, State state) {
		return env.getTypeUtils().getArrayType(t.getComponentType().accept(this, state));
	}

	@Override
	public TypeMirror visitDeclared(DeclaredType t, State state) {
		TypeElement element = (TypeElement) t.asElement();
		TypeMirror[] typeArgs = StreamEx.of(t.getTypeArguments())
				.map(type -> type.accept(this, state))
				.toArray(TypeMirror.class);
		return env.getTypeUtils().getDeclaredType(repackage(element, state), typeArgs);
	}

	private TypeElement repackage(TypeElement origin, State state) {
		Name qualifiedName = origin.getQualifiedName();
		if (qualifiedName.toString().startsWith(emuPackage)) {
			state.setChanged(true);
			return env.getElementUtils().getTypeElement(qualifiedName.subSequence(emuPackage.length() + 1, qualifiedName.length()));
		}
		return origin;
	}

}
