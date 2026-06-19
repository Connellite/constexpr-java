package io.github.connellite.constexpr.processor.hook;

import io.github.connellite.constexpr.ConstExprMain;
import io.github.connellite.constexpr.exec.ConstExprScannerTask;
import io.github.connellite.constexpr.inspect.ClassMetadata;
import io.github.connellite.constexpr.processor.ConstExprClasspath;
import io.github.connellite.constexpr.util.ConstExprClassLoaderScope;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Runs {@link ConstExprMain} after .class files exist. Required for IntelliJ IDEA, whose own
 * compiler (JPS) does not fire javac {@code TaskListener} events.
 */
public final class ConstExprPostCompile {
	private static final int MAX_ATTEMPTS = 200;
	private static final long POLL_INTERVAL_MS = 50L;
	private ConstExprPostCompile() {}

	public static void run(ProcessingEnvironment processingEnv) {
		Path outputDir;
		try {
			outputDir = resolveClassOutput(processingEnv);
		} catch (IOException ex) {
			report(processingEnv, Diagnostic.Kind.ERROR, "ConstExpr could not resolve CLASS_OUTPUT: " + ex.getMessage());
			return;
		}
		if (outputDir == null) {
			report(processingEnv, Diagnostic.Kind.ERROR, "ConstExpr CLASS_OUTPUT is not configured");
			return;
		}

		pollAndTransform(processingEnv, outputDir);
	}

	private static void pollAndTransform(ProcessingEnvironment processingEnv, Path outputDir) {
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			try {
				if (!hasPendingConstExpr(outputDir)) {
					return;
				}
				tryTransform(outputDir);
				if (!hasPendingConstExpr(outputDir)) {
					return;
				}
			} catch (IOException ex) {
				report(processingEnv, Diagnostic.Kind.ERROR,
					"ConstExpr could not scan compiled classes: " + ex.getMessage());
				return;
			} catch (RuntimeException ex) {
				report(processingEnv, Diagnostic.Kind.ERROR, "ConstExpr transformation failed: " + ex.getMessage());
				return;
			}
			try {
				Thread.sleep(POLL_INTERVAL_MS);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
		}
		try {
			if (hasPendingConstExpr(outputDir)) {
				report(processingEnv, Diagnostic.Kind.WARNING,
					"ConstExpr transformation timed out waiting for compiled classes in " + outputDir);
			}
		} catch (IOException ex) {
			report(processingEnv, Diagnostic.Kind.ERROR,
				"ConstExpr could not scan compiled classes: " + ex.getMessage());
		}
	}

	private static void report(ProcessingEnvironment processingEnv, Diagnostic.Kind kind, String message) {
		try {
			processingEnv.getMessager().printMessage(kind, message);
		} catch (RuntimeException ex) {
			System.err.println(kind + ": " + message);
		}
	}

	private static boolean tryTransform(Path outputDir) throws IOException {
		if (!Files.isDirectory(outputDir)) {
			return false;
		}
		if (!hasPendingConstExpr(outputDir)) {
			return false;
		}
		try (ConstExprClassLoaderScope ignored = ConstExprClasspath.configureForOutput(outputDir)) {
			new ConstExprMain().execute(outputDir.toAbsolutePath().toString());
		}
		return true;
	}

	private static boolean hasPendingConstExpr(Path outputDir) throws IOException {
		try (Stream<Path> files = Files.walk(outputDir)) {
			return files
				.filter(path -> path.toString().endsWith(".class"))
				.anyMatch(ConstExprPostCompile::containsConstExpr);
		}
	}

	private static boolean containsConstExpr(Path classFile) {
		try {
			ClassMetadata metadata = new ConstExprScannerTask(classFile).call();
			return metadata.containsConstExpr();
		} catch (Exception ex) {
			return false;
		}
	}

	private static Path resolveClassOutput(ProcessingEnvironment processingEnv) throws IOException {
		FileObject probe = processingEnv.getFiler()
			.createResource(StandardLocation.CLASS_OUTPUT, "", "constexpr-probe");
		URI uri = probe.toUri();
		probe.delete();
		Path path = Path.of(uri);
		Path dir = Files.isDirectory(path) ? path : path.getParent();
		return normalizeClassOutputDir(dir);
	}

	/**
	 * IntelliJ IDEA and some build tools place AP metadata under a {@code generated} subdirectory of
	 * the module output, while {@code .class} files live in the parent directory.
	 */
	private static Path normalizeClassOutputDir(Path dir) {
		if (dir == null) {
			return null;
		}
		Path current = dir.toAbsolutePath().normalize();
		for (int i = 0; i < 3; i++) {
			if (containsCompiledClass(current)) {
				return current;
			}
			Path fileName = current.getFileName();
			if (fileName == null) {
				break;
			}
			String name = fileName.toString();
			if ("generated".equals(name) || "classes".equals(name) || "java".equals(name)) {
				current = current.getParent();
				continue;
			}
			break;
		}
		return dir.toAbsolutePath().normalize();
	}

	static Path normalizeClassOutputDirForTest(Path dir) {
		return normalizeClassOutputDir(dir);
	}

	private static boolean containsCompiledClass(Path dir) {
		if (!Files.isDirectory(dir)) {
			return false;
		}
		try (Stream<Path> files = Files.walk(dir, 3)) {
			return files.anyMatch(path -> path.toString().endsWith(".class"));
		} catch (IOException ex) {
			return false;
		}
	}
}
