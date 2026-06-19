package io.github.connellite.constexpr.processor.hook;

import com.sun.source.util.TaskListener;

import io.github.connellite.constexpr.processor.ConstExprJavacUnwrap;
import io.github.connellite.constexpr.processor.ConstExprPermit;

import javax.annotation.processing.ProcessingEnvironment;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;

/**
 * Reflective access to javac internals without compile-time linkage to {@code jdk.compiler} packages.
 */
public final class JavacAccess {
	private JavacAccess() {}

	static {
		ConstExprPermit.openJavacCompilerPackages();
	}

	public static boolean installHook(ProcessingEnvironment processingEnv) {
		Object javacEnv = ConstExprJavacUnwrap.javacProcessingEnvironment(processingEnv);
		if (javacEnv == null) {
			return false;
		}
		try {
			Object context = invoke(javacEnv, "getContext");
			return ConstExprCompilationHook.install(context);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(compilerOptionsHint(), ex);
		}
	}

	static Object javacTask(Object context) throws ReflectiveOperationException {
		Class<?> basicJavacTask = Class.forName("com.sun.tools.javac.api.BasicJavacTask");
		Method instance = basicJavacTask.getMethod("instance", context.getClass());
		forceAccessible(instance);
		return instance.invoke(null, context);
	}

	static void addTaskListener(Object javacTask, TaskListener listener) throws ReflectiveOperationException {
		Method addTaskListener = javacTask.getClass().getMethod("addTaskListener", TaskListener.class);
		forceAccessible(addTaskListener);
		addTaskListener.invoke(javacTask, listener);
	}

	@SuppressWarnings("unchecked")
	public static <T> T getFromContext(Object context, Class<T> type) {
		try {
			Method get = context.getClass().getMethod("get", Class.class);
			forceAccessible(get);
			return (T) get.invoke(context, type);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Failed to read " + type.getName() + " from javac Context", ex);
		}
	}

	private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
		Method method = target.getClass().getDeclaredMethod(methodName);
		forceAccessible(method);
		return method.invoke(target);
	}

	private static void forceAccessible(AccessibleObject object) throws ReflectiveOperationException {
		ConstExprPermit.forceAccessible(object);
	}

	static String compilerOptionsHint() {
		return "ConstExpr could not hook javac. Add JVM options to the compiler process: "
			+ "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED "
			+ "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED "
			+ "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED "
			+ "--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED "
			+ "--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED "
			+ "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED";
	}
}
