package net.onedaybeard.constexpr.visitor;

import net.onedaybeard.constexpr.AsmUtil;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

public class ClinitRemoverWeaver extends ClassVisitor {

	public ClinitRemoverWeaver(ClassVisitor cv) {
		super(AsmUtil.ASM_API, cv);
	}

	@Override
	public MethodVisitor visitMethod(int access,
	                                 String name,
	                                 String desc,
	                                 String signature,
	                                 String[] exceptions) {


		if (AsmUtil.isStaticInitializer(name, desc)) {
			return null;
		} else {
			return super.visitMethod(access, name, desc, signature, exceptions);
		}
	}
}
