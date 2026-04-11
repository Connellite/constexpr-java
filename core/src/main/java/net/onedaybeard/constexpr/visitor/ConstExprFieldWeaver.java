package net.onedaybeard.constexpr.visitor;

import net.onedaybeard.constexpr.AsmUtil;
import net.onedaybeard.constexpr.ConstExpr;
import net.onedaybeard.constexpr.inspect.ClassMetadata;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Type;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;

public class ConstExprFieldWeaver extends ClassVisitor {
	private final ClassMetadata metadata;

	public ConstExprFieldWeaver(ClassMetadata metadata, ClassVisitor cv) {
		super(AsmUtil.ASM_API, cv);
		this.metadata = metadata;
	}

	@Override
	public FieldVisitor visitField(int access,
	                               String name,
	                               String desc,
	                               String signature,
	                               Object value) {

		boolean isConstExpr = metadata.fields.stream()
			.anyMatch(fd -> fd.isConstExpr
				&& fd.access == access
				&& fd.name.equals(name)
				&& fd.desc.equals(desc)
				&& (fd.signature == null || fd.signature.equals(signature)));

		if (isConstExpr) {
			try {
				Field f = getField(name);
				f.setAccessible(true);
				value = f.get(null);
				FieldVisitor fv = super.visitField(access, name, desc, signature, value);
				fv = new AnnotationRemoverVisitor(ConstExpr.class, fv);
				return fv;
			} catch (NoSuchFieldException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		} else {
			return super.visitField(access, name, desc, signature, value);
		}

	}

	private Field getField(String name) throws NoSuchFieldException {
		Class<?> type = metadata.classType;
		if (type == null) { // assuming we're called by the mojo
			try {
				ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

				String descriptor = metadata.type.getDescriptor();
				String className = Type.getType(descriptor).getClassName();
				type = classLoader.loadClass(className);
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		}

		return type.getDeclaredField(name);
	}

	static class AnnotationRemoverVisitor extends FieldVisitor {
		private final String descriptor;

		public AnnotationRemoverVisitor(Class<? extends Annotation> annotation, FieldVisitor fv) {
			super(AsmUtil.ASM_API, fv);
			descriptor = Type.getDescriptor(annotation);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			return (descriptor.equals(desc))
				? null
				: super.visitAnnotation(desc, visible);
		}
	}
}
