package net.onedaybeard.constexpr.exec;

import net.onedaybeard.constexpr.inspect.ClassMetadata;
import net.onedaybeard.constexpr.util.CallableVisitor;
import net.onedaybeard.constexpr.visitor.ConstExprScanner;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;

import java.nio.file.Path;

public class ConstExprScannerTask extends CallableVisitor<ClassMetadata> {
	private final Path path;

	public ConstExprScannerTask(Path p) {
		super(p.toFile());
		path = p;
	}

	@Override
	protected ClassMetadata process(ClassReader cr) {
		ClassMetadata info = new ClassMetadata(Type.getObjectType(cr.getClassName()), null);
		info.path = path.toFile().getAbsolutePath();

		ClassVisitor cv = new ConstExprScanner(info);
		cr.accept(cv, 0);

		return info;
	}
}
