package org.jresearch.gwt.tool.emu.apt;

import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
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

@SuppressWarnings("nls")
public class WrapClassBuilder {

	private final Builder poetBuilder;
	private final RepackageVisitor repackageVisitor;

	private WrapClassBuilder(@Nonnull final TypeElement originalClass, @Nonnull final ProcessingEnvironment env, @Nonnull final String emuPackage) {
		String className = originalClass.getSimpleName().toString();

		repackageVisitor = new RepackageVisitor(env, emuPackage);

		poetBuilder = TypeSpec.classBuilder(className)
				.addModifiers(Modifier.PUBLIC);
	}

	public static WrapClassBuilder create(@Nonnull final TypeElement originalClass, @Nonnull final ProcessingEnvironment env, @Nonnull final String emuPackage) {
		return new WrapClassBuilder(originalClass, env, emuPackage);
	}

	public WrapClassBuilder add(@Nonnull final Element element) {
		if (element instanceof ExecutableElement) {
			return addMethod((ExecutableElement) element);
		}
		if (element instanceof VariableElement) {
			return addField((VariableElement) element);
		}
		return this;
	}

	@SuppressWarnings("resource")
	public WrapClassBuilder addMethod(@Nonnull final ExecutableElement method) {
		// compare (done while build whole class)
		// show error if duplicate (done while build whole class)

		Set<Modifier> modifiers = method.getModifiers();
		if (modifiers.contains(Modifier.PRIVATE)) {
			// ignore private methods
			return this;
		}

		String methodName = method.getSimpleName().toString();
		MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName);

		methodBuilder.addModifiers(modifiers);

		StreamEx.of(method.getTypeParameters())
				.map(TypeParameterElement::asType)
				.map(t -> t.accept(repackageVisitor, new State()))
				.map(TypeVariable.class::cast)
				.map(TypeVariableName::get)
				.forEachOrdered(methodBuilder::addTypeVariable);

		StreamEx.of(method.getParameters())
				.map(ParameterSpec::get)
				.forEachOrdered(methodBuilder::addParameter);

		methodBuilder.returns(TypeName.get(method.getReturnType().accept(repackageVisitor, new State())));

		methodBuilder.varargs(method.isVarArgs());

		StreamEx.of(method.getThrownTypes())
				.map(t -> t.accept(repackageVisitor, new State()))
				.map(TypeName::get)
				.forEach(methodBuilder::addException);

		if (hasReturn(method)) {
			methodBuilder.addStatement("return null");
		}

		poetBuilder.addMethod(methodBuilder.build());

		return this;
	}

	private static boolean hasReturn(ExecutableElement method) {
		TypeMirror returnType = method.getReturnType();
		return !(returnType instanceof NoType) || returnType.getKind() != TypeKind.VOID;
	}

	public WrapClassBuilder addField(@Nonnull final VariableElement field) {
		Set<Modifier> modifiers = field.getModifiers();
		if (modifiers.contains(Modifier.PRIVATE)) {
			// ignore private fields
			return this;
		}

		String fieldName = field.getSimpleName().toString();

		poetBuilder.addField(TypeName.get(field.asType().accept(repackageVisitor, new State())), fieldName, modifiers.toArray(new Modifier[modifiers.size()]));

		return this;
	}

	public TypeSpec build() {
		return poetBuilder.build();
	}

}
