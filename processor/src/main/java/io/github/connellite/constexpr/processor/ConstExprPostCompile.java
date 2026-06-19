package io.github.connellite.constexpr.processor;

import io.github.connellite.constexpr.ConstExprMain;
import io.github.connellite.constexpr.exec.ConstExprScannerTask;
import io.github.connellite.constexpr.inspect.ClassMetadata;
import io.github.connellite.constexpr.util.ConstExprClassLoaderScope;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Runs {@link ConstExprMain} after .class files exist. Required for IntelliJ IDEA, whose own
 * compiler (JPS) does not fire javac {@code TaskListener} events.
 */
final class ConstExprPostCompile {
	private static final int MAX_ATTEMPTS = 200;
	private static final long POLL_INTERVAL_MS = 50L;
	private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

	private ConstExprPostCompile() {}

	static void schedule(ProcessingEnvironment processingEnv) {
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

		ExecutorService executor = Executors.newSingleThreadExecutor(namedThreadFactory());
		executor.submit(() -> {
			try {
				pollAndTransform(processingEnv, outputDir);
			} finally {
				executor.shutdown();
			}
		});
	}

	private static void pollAndTransform(ProcessingEnvironment processingEnv, Path outputDir) {
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			try {
				if (tryTransform(outputDir)) {
					return;
				}
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
		report(processingEnv, Diagnostic.Kind.WARNING,
			"ConstExpr transformation timed out waiting for compiled classes in " + outputDir);
	}

	private static ThreadFactory namedThreadFactory() {
		return runnable -> new Thread(
			runnable,
			"constexpr-post-compile-" + THREAD_COUNTER.incrementAndGet());
	}

	private static void report(ProcessingEnvironment processingEnv, Diagnostic.Kind kind, String message) {
		// This runs on a background thread after the processing rounds finished, so the compiler may have
		// already torn down its Messager. Fall back to stderr instead of letting a teardown error mask the
		// real diagnostic.
		try {
			processingEnv.getMessager().printMessage(kind, message);
		} catch (RuntimeException ex) {
			System.err.println(kind + ": " + message);
		}
	}

	private static boolean tryTransform(Path outputDir) {
		if (!Files.isDirectory(outputDir)) {
			return false;
		}
		try {
			if (!hasPendingConstExpr(outputDir)) {
				return false;
			}
			try (ConstExprClassLoaderScope ignored = ConstExprClasspath.configureForOutput(outputDir)) {
				new ConstExprMain().execute(outputDir.toAbsolutePath().toString());
			}
			return true;
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
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
		return Files.isDirectory(path) ? path : path.getParent();
	}
}
