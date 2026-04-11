package net.onedaybeard.constexpr.visitor;

import net.onedaybeard.constexpr.AsmUtil;
import net.onedaybeard.constexpr.inspect.ClassMetadata;
import net.onedaybeard.constexpr.inspect.MethodDescriptor;
import net.onedaybeard.constexpr.transformer.CinitConstExprTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import java.util.Arrays;

public class ConstExprMethodWeaver extends ClassVisitor {
	private final ClassMetadata metadata;

	public ConstExprMethodWeaver(ClassMetadata metadata, ClassVisitor cv) {
		super(AsmUtil.ASM_API, cv);
		this.metadata = metadata;
	}

	@Override
	public MethodVisitor visitMethod(int access,
	                                 String name,
	                                 String desc,
	                                 String signature,
	                                 String[] exceptions) {

		boolean isConstExpr = metadata.methods.stream()
			.anyMatch(md -> md.isConstExpr
				&& md.access == access
				&& md.name.equals(name)
				&& md.desc.equals(desc)
				&& (md.signature == null || md.signature.equals(signature))
				&& Arrays.equals(md.exceptions, exceptions));

		if (AsmUtil.isStaticInitializer(name, desc)) {
			MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
			MethodDescriptor descriptor = metadata.methods.stream()
				.filter(AsmUtil::isStaticInitializer)
				.findFirst()
				.get();

			return new CinitConstExprTransformer(metadata, descriptor, mv);
		} else if (isConstExpr) {
			return null; // removing "compile-time function"
		} else {
			return super.visitMethod(access, name, desc, signature, exceptions);
		}
	}
}
