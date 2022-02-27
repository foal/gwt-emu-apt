package org.jresearch.gwt.tool.emu.apt;

import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeSpec.Builder;
import com.squareup.javapoet.TypeVariableName;

import one.util.streamex.StreamEx;

public class WrapClassBuilder {

	private final Builder poetBuilder;
	private final RepackageVisitor repackageVisitor;

	@SuppressWarnings("resource")
	private WrapClassBuilder(@Nonnull final TypeElement originalClass, @Nonnull final ProcessingEnvironment env, @Nonnull final String emuPackage) {
		String className = originalClass.getSimpleName().toString();

		repackageVisitor = new RepackageVisitor(env, emuPackage);

		poetBuilder = TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC);

		TypeMirror superclass = originalClass.getSuperclass();
		if (superclass != null) {
			TypeMirror newSuperclass = superclass.accept(repackageVisitor, new State());
			if (!env.getTypeUtils().asElement(newSuperclass).getModifiers().contains(Modifier.FINAL)) {
				poetBuilder.superclass(superclass.accept(repackageVisitor, new State()));
			}
		}

		StreamEx.of(originalClass.getInterfaces())
				.map(i -> i.accept(repackageVisitor, new State()))
				.forEach(poetBuilder::addSuperinterface);
	}

	public static WrapClassBuilder create(@Nonnull final TypeElement originalClass, @Nonnull final ProcessingEnvironment env, @Nonnull final String emuPackage) {
		return new WrapClassBuilder(originalClass, env, emuPackage);
	}

	public WrapClassBuilder add(@Nonnull final Element element) {
		if (element instanceof ExecutableElement) {
			return addMethod((ExecutableElement) element, element.getKind() == ElementKind.CONSTRUCTOR);
		}
		if (element instanceof VariableElement) {
			return addField((VariableElement) element);
		}
		return this;
	}

	@SuppressWarnings("resource")
	public WrapClassBuilder addMethod(@Nonnull final ExecutableElement method, boolean constructor) {
		// compare (done while build whole class)
		// show error if duplicate (done while build whole class)

		Set<Modifier> modifiers = method.getModifiers();
		if (modifiers.contains(Modifier.PRIVATE)) {
			// ignore private methods
			return this;
		}

		MethodSpec.Builder methodBuilder = constructor
				? MethodSpec.constructorBuilder()
				: MethodSpec.methodBuilder(method.getSimpleName().toString());

		methodBuilder.addModifiers(modifiers);

		StreamEx.of(method.getTypeParameters())
				.map(TypeParameterElement::asType)
				.map(t -> t.accept(repackageVisitor, new State()))
				.map(TypeVariable.class::cast)
				.map(TypeVariableName::get)
				.forEachOrdered(methodBuilder::addTypeVariable);

		StreamEx.of(method.getParameters())
				.map(this::asParameterSpec)
				.forEachOrdered(methodBuilder::addParameter);

		if (!constructor) {
			methodBuilder.returns(TypeName.get(method.getReturnType().accept(repackageVisitor, new State())));
		}

		methodBuilder.varargs(method.isVarArgs());

		StreamEx.of(method.getThrownTypes())
				.map(t -> t.accept(repackageVisitor, new State()))
				.map(TypeName::get)
				.forEach(methodBuilder::addException);

		if (!constructor && hasReturn(method)) {
			methodBuilder.addStatement("throw new UnsupportedOperationException(\"GWT super source wrap class\")");
		}

		poetBuilder.addMethod(methodBuilder.build());

		return this;
	}

	private ParameterSpec asParameterSpec(VariableElement element) {
		TypeName type = TypeName.get(element.asType().accept(repackageVisitor, new State()));
		String name = element.getSimpleName().toString();
		return ParameterSpec.builder(type, name)
				.addModifiers(element.getModifiers())
				.build();
	}

	private static boolean hasReturn(ExecutableElement method) {
		TypeMirror returnType = method.getReturnType();
		return !(returnType instanceof NoType) || returnType.getKind() != TypeKind.VOID;
	}

	@SuppressWarnings("resource")
	public WrapClassBuilder addField(@Nonnull final VariableElement field) {
		Set<Modifier> modifiers = field.getModifiers();
		if (modifiers.contains(Modifier.PRIVATE)) {
			// ignore private fields
			return this;
		}
		// Remove the final mod. from cloned variable to skip initialization
		Modifier[] wrapModifiers = StreamEx.of(modifiers)
				.remove(Modifier.FINAL::equals)
				.toArray(Modifier.class);

		String fieldName = field.getSimpleName().toString();

		poetBuilder.addField(TypeName.get(field.asType().accept(repackageVisitor, new State())), fieldName, wrapModifiers);

		return this;
	}

	public TypeSpec build() {
		return poetBuilder.build();
	}

}
