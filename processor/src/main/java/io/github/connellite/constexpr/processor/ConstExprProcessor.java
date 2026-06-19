package io.github.connellite.constexpr.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import java.util.Map;
import java.util.Set;

/**
 * Annotation processor discovered via {@code META-INF/services/javax.annotation.processing.Processor}.
 * <ul>
 *     <li>javac / {@code JavaCompiler}: hooks {@code TaskListener} when possible.</li>
 *     <li>IntelliJ IDEA (JPS): post-compile polling on the final AP round.</li>
 * </ul>
 */
@SupportedAnnotationTypes("io.github.connellite.constexpr.ConstExpr")
@SupportedOptions("constexpr.skip")
public class ConstExprProcessor extends AbstractProcessor {
	private boolean skip;
	private boolean javacHookInstalled;
	private boolean sawConstExpr;

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		skip = isSkipped(processingEnv.getOptions());
		if (skip) {
			return;
		}
		try {
			javacHookInstalled = JavacAccess.installHook(processingEnv);
		} catch (RuntimeException ex) {
			processingEnv.getMessager().printMessage(
				Diagnostic.Kind.WARNING,
				ex.getMessage());
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
		if (!annotations.isEmpty()) {
			sawConstExpr = true;
		}
		if (!skip && !javacHookInstalled && sawConstExpr && roundEnv.processingOver()) {
			ConstExprPostCompile.schedule(processingEnv);
		}
		return false;
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
