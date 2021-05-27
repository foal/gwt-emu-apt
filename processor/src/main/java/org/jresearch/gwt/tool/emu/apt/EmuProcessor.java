package org.jresearch.gwt.tool.emu.apt;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import org.jresearch.gwt.tool.emu.apt.annotation.Wrap;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.JavaFile.Builder;
import com.squareup.javapoet.TypeSpec;

import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

@AutoService(Processor.class)
public class EmuProcessor extends AbstractProcessor {

	private final AtomicInteger round = new AtomicInteger();

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return ImmutableSet.of(Wrap.class.getName());
	}

//	@Override
//	public SourceVersion getSupportedSourceVersion() {
//		return SourceVersion.latestSupported();
//	}

	@SuppressWarnings({ "resource", "boxing" })
	@Override
	public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
		processingEnv.getMessager().printMessage(Kind.NOTE, String.format("Call the round %d for %s", round.incrementAndGet(), StreamEx
				.of(roundEnv.getRootElements()).map(Element::getSimpleName).joining(", ")));

		// Group methods by classes
		ImmutableSetMultimap<Element, Element> roundMethods = StreamEx.of(roundEnv.getElementsAnnotatedWith(Wrap.class))
				.filterBy(Element::getKind, ElementKind.CLASS)
				.flatCollection(Element::getEnclosedElements)
				.filter(EmuProcessor::isNonPrivateElement)
				.toListAndThen(EmuProcessor::toMultiMap);

		processingEnv.getMessager().printMessage(Kind.NOTE, String.format("Element list updated: %s", roundMethods));

		EntryStream.of(roundMethods.asMap())
				.filterKeys(this::isPackageClass)
				.mapKeys(TypeElement.class::cast)
				.forKeyValue(this::generateWrap);
		return true;
	}

	private static ImmutableSetMultimap<Element, Element> toMultiMap(List<? extends Element> elements) {
		ImmutableSetMultimap.Builder<Element, Element> result = ImmutableSetMultimap.builder();
		elements.forEach(e -> result.put(e.getEnclosingElement(), e));
		return result.build();
	}

	private static boolean isNonPrivateElement(Element element) {
		return !element.getModifiers().contains(Modifier.PRIVATE);
	}

	private boolean isPackageClass(Element element) {
		boolean pass = element.getKind() == ElementKind.CLASS && element.getEnclosingElement().getKind() == ElementKind.PACKAGE;
		if (!pass) {
			processingEnv.getMessager().printMessage(Kind.ERROR, "The class shouldn't be embeded to create the JRE wrap", element);
		}
		return pass;
	}

	private Void generateWrap(TypeElement originalClass, Collection<Element> classElements) {
		Optional<String> emuPackage = getEmuPackage(originalClass);
		if (emuPackage.isPresent()) {
			final WrapClassBuilder builder = WrapClassBuilder.create(originalClass, processingEnv, emuPackage.get());
			classElements.forEach(builder::add);
			TypeSpec spec = builder.build();

			Name packageName = newPackage(originalClass, emuPackage.get());
			final Builder javaFileBuilder = JavaFile.builder(packageName.toString(), spec).indent("\t");
			JavaFile javaFile = javaFileBuilder.build();

			try {
				final JavaFileObject jfo = processingEnv.getFiler().createSourceFile(String.format("%s.%s", packageName, spec.name), originalClass);
				try (Writer wr = jfo.openWriter()) {
					javaFile.writeTo(wr);
				}
			} catch (final IOException e) {
				processingEnv.getMessager().printMessage(Kind.NOTE, String.format("Can't generate versioned controller class: %s", e.getMessage()));
			}
		}
		return null;
	}

	private Optional<String> getEmuPackage(TypeElement originalClass) {
		Wrap annotation = originalClass.getAnnotation(Wrap.class);
		if (annotation == null) {
			processingEnv.getMessager().printMessage(Kind.ERROR, "The class %s doesn't have annotation Wrap", originalClass);
			return Optional.empty();
		}
		PackageElement packageElement = (PackageElement) originalClass.getEnclosingElement();
		Name packageName = packageElement.getQualifiedName();
		String emuPackage = annotation.value();
		if (emuPackage.isEmpty() || !packageName.toString().startsWith(emuPackage)) {
			processingEnv.getMessager().printMessage(Kind.ERROR, "The emu package %s is incorrect (empty or is not a prefix of class package", originalClass);
			return Optional.empty();
		}
		return Optional.of(emuPackage);
	}

	private Name newPackage(TypeElement originalClass, String emuPackage) {
		PackageElement packageElement = (PackageElement) originalClass.getEnclosingElement();
		Name packageName = packageElement.getQualifiedName();
		return processingEnv.getElementUtils().getName(packageName.subSequence(emuPackage.length() + 1, packageName.length()));
	}

}
