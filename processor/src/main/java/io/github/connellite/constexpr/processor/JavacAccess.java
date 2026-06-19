package io.github.connellite.constexpr.processor;

import com.sun.source.util.TaskListener;

import javax.annotation.processing.ProcessingEnvironment;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;

/**
 * Reflective access to javac internals without compile-time linkage to {@code jdk.compiler} packages.
 */
final class JavacAccess {
	private static final String JAVAC_PROCESSING_ENV =
		"com.sun.tools.javac.processing.JavacProcessingEnvironment";

	private JavacAccess() {}

	static {
		ConstExprPermit.openJavacCompilerPackages();
	}

	static boolean installHook(ProcessingEnvironment processingEnv) {
		ProcessingEnvironment javacEnv = unwrapJavacEnvironment(processingEnv);
		if (!JAVAC_PROCESSING_ENV.equals(javacEnv.getClass().getName())) {
			return false;
		}
		try {
			Object context = invoke(javacEnv, "getContext");
			ConstExprCompilationHook.install(context);
			return true;
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(compilerOptionsHint(), ex);
		}
	}

	private static ProcessingEnvironment unwrapJavacEnvironment(ProcessingEnvironment env) {
		if (JAVAC_PROCESSING_ENV.equals(env.getClass().getName())) {
			return env;
		}
		for (Class<?> type = env.getClass(); type != null; type = type.getSuperclass()) {
			for (java.lang.reflect.Field field : type.getDeclaredFields()) {
				if (!ProcessingEnvironment.class.isAssignableFrom(field.getType())) {
					continue;
				}
				try {
					ConstExprPermit.forceAccessible(field);
					Object value = field.get(env);
					if (value instanceof ProcessingEnvironment delegate) {
						return unwrapJavacEnvironment(delegate);
					}
				} catch (ReflectiveOperationException ignored) {
					// try next field
				}
			}
		}
		return env;
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
	static <T> T getFromContext(Object context, Class<T> type) {
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
