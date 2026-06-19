package io.github.connellite.constexpr.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import io.github.connellite.constexpr.processor.hook.ConstExprCompilationHook;
import io.github.connellite.constexpr.processor.hook.ConstExprPostCompile;
import io.github.connellite.constexpr.processor.hook.JavacAccess;
import io.github.connellite.constexpr.processor.intercept.ConstExprJavacInstaller;
import io.github.connellite.constexpr.processor.intercept.ConstExprJavacInstaller.InstallResult;

import javax.tools.Diagnostic;

import java.util.Map;
import java.util.Set;

/**
 * Annotation processor discovered via {@code META-INF/services/javax.annotation.processing.Processor}.
 * <ul>
 *     <li>javac / IntelliJ / {@code JavaCompiler}: intercepts {@code .class} output streams (Lombok-style).</li>
 *     <li>Fallback: post-compile scan on the final AP round.</li>
 * </ul>
 */
@SupportedAnnotationTypes("io.github.connellite.constexpr.ConstExpr")
@SupportedOptions("constexpr.skip")
public class ConstExprProcessor extends AbstractProcessor {
	private boolean skip;
	private boolean streamInterceptorInstalled;
	private boolean compilationHookInstalled;

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		skip = isSkipped(processingEnv.getOptions());
		if (skip) {
			return;
		}
		InstallResult streamInterceptor = ConstExprJavacInstaller.installWithDiagnostics(processingEnv);
		streamInterceptorInstalled = streamInterceptor.installed();
		if (streamInterceptor.message() != null) {
			reportWarning(streamInterceptor.message());
		}

		try {
			compilationHookInstalled = JavacAccess.installHook(processingEnv);
		} catch (RuntimeException ex) {
			reportWarning(ex.getMessage());
		}
		if (!streamInterceptorInstalled && !compilationHookInstalled) {
			reportWarning("ConstExpr could not install a javac integration hook; "
				+ "the final post-compile fallback will still try to transform class output.");
		}
	}

	/**
	 * Reports the latest source version the running JDK supports rather than a fixed release. This mirrors
	 * Project Lombok and prevents javac from emitting a "Supported source version ... less than -source"
	 * warning when compiling with a newer {@code --release}/{@code -source} level.
	 */
	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		if (!skip && roundEnv.processingOver()) {
			ConstExprPostCompile.run(processingEnv);
			ConstExprCompilationHook.clearCompilationHookState();
		}
		return false;
	}

	private void reportWarning(String message) {
		if (message == null || message.isBlank()) {
			return;
		}
		processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, message);
	}

	private static boolean isSkipped(Map<String, String> options) {
		if (Boolean.getBoolean("constexpr.skip")) {
			return true;
		}
		String option = options.get("constexpr.skip");
		if (Boolean.parseBoolean(option)) {
			return true;
		}
		String env = System.getenv("CONSTEXPR_SKIP");
		return Boolean.parseBoolean(env);
	}
}
