package io.github.connellite.constexpr.visitor;

import io.github.connellite.constexpr.AsmUtil;
import io.github.connellite.constexpr.inspect.FieldDescriptor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.FieldVisitor;

import java.lang.annotation.Annotation;

class FieldAnnotationScanner extends FieldVisitor {
	private final FieldDescriptor field;
	private AnnotationScanner<FieldDescriptor> scanner;

	public FieldAnnotationScanner(FieldVisitor fv,
	                              FieldDescriptor field) {

		super(AsmUtil.ASM_API, fv);
		this.field = field;
	}

	public FieldAnnotationScanner scanFor(Class<? extends Annotation> annotation,
	                                      AnnotationFoundListener<FieldDescriptor> listener) {

		scanner = new AnnotationScanner<>(field, annotation, listener);
		return this;
	}

	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		scanner.visitAnnotation(desc);
		return super.visitAnnotation(desc, visible);
	}

}
