package io.github.connellite.constexpr.processor.intercept;

import io.github.connellite.constexpr.exec.ConstExprScannerTask;
import io.github.connellite.constexpr.exec.ConstExprTransformer;
import io.github.connellite.constexpr.inspect.ClassMetadata;
import io.github.connellite.constexpr.util.ConstExprClassLoaderScope;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Transforms a single in-memory {@code .class} payload, used when intercepting javac output streams.
 */
final class ConstExprClassBytesTransformer {
	private ConstExprClassBytesTransformer() {}

	static byte[] transformIfNeeded(byte[] original, String className, List<File> classpath) throws IOException {
		if (original == null || original.length == 0) {
			return original;
		}

		Path tempDir = Files.createTempDirectory("constexpr-class-bytes-");
		try {
			Path classFile = tempDir.resolve(className.replace('.', '/') + ".class");
			Files.createDirectories(classFile.getParent());
			Files.write(classFile, original);

			ClassMetadata metadata = new ConstExprScannerTask(classFile).call();
			if (!metadata.containsConstExpr()) {
				return original;
			}

			List<File> entries = new ArrayList<>();
			entries.add(tempDir.toFile());
			entries.addAll(classpath);
			byte[] transformed;
			try (ConstExprClassLoaderScope ignored = ConstExprClassLoaderScope.open(entries)) {
				byte[] bytes = new ConstExprTransformer(metadata).call();
				transformed = bytes != null ? bytes : Files.readAllBytes(classFile);
			}
			return transformed;
		} catch (Exception ex) {
			throw new IOException("ConstExpr could not transform " + className, ex);
		} finally {
			deleteRecursively(tempDir);
		}
	}

	private static void deleteRecursively(Path root) {
		if (!Files.exists(root)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
					// Windows may keep class files locked briefly after the scoped loader closes.
				}
			}
		} catch (IOException ignored) {
			// Best effort cleanup for temporary transform directories.
		}
	}
}
