package io.github.connellite.constexpr.processor;

import io.github.connellite.constexpr.exec.ConstExprScannerTask;
import io.github.connellite.constexpr.inspect.ClassMetadata;
import io.github.connellite.constexpr.inspect.FieldDescriptor;
import org.junit.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end tests that drive a real {@link JavaCompiler} task with this processor on the classpath and
 * assert on the resulting bytecode. The samples deliberately use non-constant initializers (method calls)
 * so the assertions exercise the {@code @ConstExpr} transformation itself rather than javac's own
 * compile-time constant folding.
 */
public class ConstExprProcessorTest {

	@Test
	public void bakesConstExprFieldValuesAndStripsAnnotation() throws Exception {
		Path output = compile("Constants", """
			package sample;

			import io.github.connellite.constexpr.ConstExpr;

			public final class Constants {
				@ConstExpr
				public static final String GREETING = "Hello, ".concat("World");

				@ConstExpr
				public static final int ANSWER = Integer.parseInt("42");

				private Constants() {
				}
			}
			""");

		ClassMetadata metadata = scan(output, "Constants");

		FieldDescriptor greeting = field(metadata, "GREETING");
		assertFalse("@ConstExpr annotation must be stripped after baking", greeting.isConstExpr);
		assertEquals("Hello, World", greeting.value);

		FieldDescriptor answer = field(metadata, "ANSWER");
		assertFalse(answer.isConstExpr);
		assertEquals(42, answer.value);
	}

	@Test
	public void removesConstExprMethods() throws Exception {
		Path output = compile("BuildTime", """
			package sample;

			import io.github.connellite.constexpr.ConstExpr;

			public final class BuildTime {
				@ConstExpr
				public static final String VALUE = compute();

				@ConstExpr
				public static String compute() {
					return "computed-" + Integer.toString(21 + 21);
				}
			}
			""");

		ClassMetadata metadata = scan(output, "BuildTime");

		assertFalse("@ConstExpr method must be removed from the class",
			metadata.methods.stream().anyMatch(md -> "compute".equals(md.name)));

		FieldDescriptor value = field(metadata, "VALUE");
		assertFalse(value.isConstExpr);
		assertEquals("computed-42", value.value);
	}

	@Test
	public void skipOptionLeavesClassesUntransformed() throws Exception {
		Path output = compile("Constants", """
			package sample;

			import io.github.connellite.constexpr.ConstExpr;

			public final class Constants {
				@ConstExpr
				public static final String GREETING = "Hello, ".concat("World");

				private Constants() {
				}
			}
			""", "-Aconstexpr.skip=true");

		ClassMetadata metadata = scan(output, "Constants");

		FieldDescriptor greeting = field(metadata, "GREETING");
		assertTrue("@ConstExpr must remain when processing is skipped", greeting.isConstExpr);
		assertNull("value must not be baked when processing is skipped", greeting.value);
	}

	@Test
	public void leavesClassesWithoutConstExprUntouched() throws Exception {
		Path output = compile("Plain", """
			package sample;

			public final class Plain {
				public static final String NAME = "plain".toUpperCase();
			}
			""");

		ClassMetadata metadata = scan(output, "Plain");
		assertFalse(metadata.containsConstExpr());

		FieldDescriptor name = field(metadata, "NAME");
		assertFalse(name.isConstExpr);
		assertNull("a non-annotated runtime initializer must not be folded", name.value);
	}

	private static Path compile(String simpleName, String source, String... options) throws IOException {
		Path workDir = Files.createTempDirectory("constexpr-processor-test");
		Path sourceDir = workDir.resolve("src");
		Path outputDir = workDir.resolve("classes");
		Files.createDirectories(sourceDir);
		Files.createDirectories(outputDir);

		Path sourceFile = sourceDir.resolve(simpleName + ".java");
		Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		assertNotNull("JDK compiler required", compiler);

		try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
			diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {

			fileManager.setLocation(StandardLocation.CLASS_PATH, compilerClasspath());
			fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outputDir.toFile()));

			Iterable<? extends JavaFileObject> units =
				fileManager.getJavaFileObjectsFromFiles(List.of(sourceFile.toFile()));

			JavaCompiler.CompilationTask task = compiler.getTask(
				null,
				fileManager,
				diagnostics,
				List.of(options),
				null,
				units);

			assertTrue("compilation failed: " + formatDiagnostics(diagnostics), task.call());
		}
		return outputDir;
	}

	private static ClassMetadata scan(Path outputDir, String simpleName) throws Exception {
		Path classFile = outputDir.resolve("sample").resolve(simpleName + ".class");
		assertTrue(simpleName + ".class missing", Files.exists(classFile));
		return new ConstExprScannerTask(classFile).call();
	}

	private static FieldDescriptor field(ClassMetadata metadata, String name) {
		return metadata.fields.stream()
			.filter(fd -> name.equals(fd.name))
			.findFirst()
			.orElseThrow(() -> new AssertionError("field '" + name + "' not found"));
	}

	private static List<File> compilerClasspath() {
		List<File> classpath = new ArrayList<>();
		classpath.add(moduleClasses("api"));
		classpath.add(moduleClasses("core"));
		classpath.add(moduleClasses("processor"));

		for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
			if (entry.contains("asm-") && entry.endsWith(".jar")) {
				classpath.add(new File(entry));
			}
		}
		return classpath;
	}

	private static File moduleClasses(String module) {
		File classes = new File("../" + module + "/target/classes");
		assertTrue(module + " must be built (target/classes)", classes.isDirectory());
		return classes;
	}

	private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
		StringBuilder sb = new StringBuilder();
		for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
			sb.append(diagnostic.getKind())
				.append(": ")
				.append(diagnostic.getMessage(Locale.ROOT))
				.append(System.lineSeparator());
		}
		return sb.toString();
	}
}
