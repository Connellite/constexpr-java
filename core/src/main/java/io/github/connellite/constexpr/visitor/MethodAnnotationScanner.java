package io.github.connellite.constexpr.visitor;

import io.github.connellite.constexpr.AsmUtil;
import io.github.connellite.constexpr.inspect.MethodDescriptor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import java.lang.annotation.Annotation;

public class MethodAnnotationScanner extends MethodVisitor {
	private final MethodDescriptor descriptor;
	private AnnotationScanner<MethodDescriptor> scanner;

	public MethodAnnotationScanner(MethodVisitor mv, MethodDescriptor descriptor) {
		super(AsmUtil.ASM_API, mv);
		this.descriptor = descriptor;
	}

	public MethodAnnotationScanner scanFor(Class<? extends Annotation> annotation,
	                                       AnnotationFoundListener<MethodDescriptor> listener) {

		scanner = new AnnotationScanner<>(descriptor, annotation, listener);
		return this;
	}

	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		scanner.visitAnnotation(desc);
		return super.visitAnnotation(desc, visible);
	}
}
