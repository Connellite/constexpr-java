package io.github.connellite.constexpr.processor.hook;

import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;

import io.github.connellite.constexpr.ConstExprMain;
import io.github.connellite.constexpr.inspect.ClassMetadata;
import io.github.connellite.constexpr.processor.ConstExprClasspath;
import io.github.connellite.constexpr.processor.ConstExprJavacUnwrap;
import io.github.connellite.constexpr.util.ConstExprClassLoaderScope;

import javax.tools.JavaFileManager;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs {@link ConstExprMain} when javac finishes, still inside {@code CompilationTask.call()}.
 */
public final class ConstExprCompilationHook implements TaskListener {
	private static final Logger LOGGER = Logger.getLogger(ConstExprCompilationHook.class.getName());

	private static final Set<Object> INSTALLED_CONTEXTS =
		Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	private static final ThreadLocal<Boolean> COMPILATION_HOOK_FIRED =
		ThreadLocal.withInitial(() -> Boolean.FALSE);

	private final StandardJavaFileManager fileManager;

	private ConstExprCompilationHook(StandardJavaFileManager fileManager) {
		this.fileManager = fileManager;
	}

	static boolean install(Object context) {
		if (!INSTALLED_CONTEXTS.add(context)) {
			return true;
		}

		JavaFileManager javaFileManager = JavacAccess.getFromContext(context, JavaFileManager.class);
		StandardJavaFileManager standardFileManager = ConstExprJavacUnwrap.standardJavaFileManager(javaFileManager);
		if (standardFileManager == null) {
			return false;
		}

		try {
			Object javacTask = JavacAccess.javacTask(context);
			JavacAccess.addTaskListener(javacTask, new ConstExprCompilationHook(standardFileManager));
			return true;
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(
				"ConstExpr could not register javac task listener. " + JavacAccess.compilerOptionsHint(), ex);
		}
	}

	static boolean compilationHookFired() {
		return COMPILATION_HOOK_FIRED.get();
	}

	public static void clearCompilationHookState() {
		COMPILATION_HOOK_FIRED.remove();
	}

	@Override
	public void finished(TaskEvent e) {
		if (e.getKind() != TaskEvent.Kind.COMPILATION) {
			return;
		}
		COMPILATION_HOOK_FIRED.set(Boolean.TRUE);
		try {
			transformOutput();
		} catch (IOException ex) {
			throw new RuntimeException("ConstExpr transformation failed", ex);
		}
	}

	private void transformOutput() throws IOException {
		Iterable<? extends File> outputDirs = fileManager.getLocation(StandardLocation.CLASS_OUTPUT);
		if (outputDirs == null) {
			return;
		}

		for (File outputDir : outputDirs) {
			if (outputDir == null || !outputDir.isDirectory()) {
				continue;
			}
			transformDirectory(outputDir);
		}
	}

	private void transformDirectory(File outputDir) throws IOException {
		ConstExprMain.Stats stats;
		try (ConstExprClassLoaderScope ignored = ConstExprClasspath.configure(fileManager)) {
			stats = new ConstExprMain().execute(outputDir.getAbsolutePath());
		}
		long transformed = stats.scanned.stream()
			.filter(ClassMetadata::containsConstExpr)
			.count();
		if (transformed > 0) {
			LOGGER.log(Level.FINE,
				() -> "Transformed " + transformed + " @ConstExpr class(es) in " + outputDir);
		}
	}
}
