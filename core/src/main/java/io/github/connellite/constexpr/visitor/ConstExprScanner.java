package io.github.connellite.constexpr.visitor;

import io.github.connellite.constexpr.AsmUtil;
import io.github.connellite.constexpr.ConstExpr;
import io.github.connellite.constexpr.inspect.FieldDescriptor;
import io.github.connellite.constexpr.inspect.ClassMetadata;
import io.github.connellite.constexpr.inspect.MethodDescriptor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

public class ConstExprScanner extends ClassVisitor {
	private final ClassMetadata metadata;

	public ConstExprScanner(ClassMetadata metadata) {
		super(AsmUtil.ASM_API);
		this.metadata = metadata;
	}

	@Override
	public MethodVisitor visitMethod(int access,
	                                 String name,
	                                 String desc,
	                                 String signature,
	                                 String[] exceptions) {

		MethodDescriptor descriptor = new MethodDescriptor(access, name, desc, signature, exceptions);
		metadata.add(descriptor);

        return new MethodAnnotationScanner(null, descriptor)
            .scanFor(ConstExpr.class, md -> md.isConstExpr = true);
	}

	@Override
	public FieldVisitor visitField(int access,
	                               String name,
	                               String desc,
	                               String signature,
	                               Object value) {

		FieldDescriptor descriptor = new FieldDescriptor(access, name, desc, signature, value);
		metadata.add(descriptor);

		FieldVisitor fv = super.visitField(access, name, desc, signature, value);
		fv = new FieldAnnotationScanner(fv, descriptor)
			.scanFor(ConstExpr.class, fd -> fd.isConstExpr = true);

		return fv;
	}

	@Override
	public void visitEnd() {
		super.visitEnd();

		metadata.fields
			.forEach(FieldDescriptor::validate);
		metadata.methods
			.forEach(MethodDescriptor::validate);
	}
}
