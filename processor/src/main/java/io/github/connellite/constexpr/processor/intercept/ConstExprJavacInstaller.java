package io.github.connellite.constexpr.processor.intercept;

import io.github.connellite.constexpr.processor.ConstExprJavacUnwrap;
import io.github.connellite.constexpr.processor.ConstExprPermit;
import io.github.connellite.constexpr.processor.hook.JavacAccess;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.JavaFileManager;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * Installs an intercepting {@link JavaFileManager} into javac's {@code Context}, mirroring
 * {@code LombokProcessor.placePostCompileAndDontMakeForceRoundDummiesHook()}.
 * <p>
 * Ported from:
 * <ul>
 *     <li><a href="https://github.com/projectlombok/lombok/blob/master/src/core/lombok/javac/apt/LombokProcessor.java#L153-L182">
 *     {@code LombokProcessor.placePostCompileAndDontMakeForceRoundDummiesHook()}</a></li>
 *     <li><a href="https://github.com/projectlombok/lombok/blob/master/src/core/lombok/javac/apt/LombokProcessor.java#L184-L224">
 *     {@code LombokProcessor.replaceFileManagerJdk*()}</a></li>
 *     <li><a href="https://github.com/projectlombok/lombok/blob/master/src/core/lombok/javac/apt/LombokProcessor.java#L226-L255">
 *     NetBeans editor workarounds</a></li>
 *     <li><a href="https://github.com/projectlombok/lombok/blob/master/src/core/lombok/javac/apt/LombokProcessor.java#L256-L308">
 *     processor class loader wrapper</a></li>
 * </ul>
 */
public final class ConstExprJavacInstaller {
	private ConstExprJavacInstaller() {}

	public static boolean install(ProcessingEnvironment processingEnv) {
		return installWithDiagnostics(processingEnv).installed();
	}

	public static InstallResult installWithDiagnostics(ProcessingEnvironment processingEnv) {
		Object javacEnv = ConstExprJavacUnwrap.javacProcessingEnvironment(processingEnv);
		if (javacEnv == null) {
			return InstallResult.notInstalled("ConstExpr stream interception is available only in javac processing environments");
		}
		Object javacFiler = ConstExprJavacUnwrap.javacFiler(processingEnv.getFiler());
		if (javacFiler == null) {
			return InstallResult.notInstalled("ConstExpr could not unwrap javac Filer for stream interception");
		}
		try {
			stopJavacProcessingEnvironmentFromClosingProcessorClassLoader(javacEnv);
			forceMultipleRoundsInNetBeansEditor(javacEnv);
			Object context = invoke(javacEnv, "getContext");
			disablePartialReparseInNetBeansEditor(context);
			JavaFileManager current = JavacAccess.getFromContext(context, JavaFileManager.class);
			if (current instanceof ConstExprInterceptingJavaFileManager) {
				return InstallResult.success();
			}
			if (current == null) {
				return InstallResult.notInstalled("ConstExpr could not read javac JavaFileManager from Context");
			}

			ConstExprInterceptingJavaFileManager intercepting =
				new ConstExprInterceptingJavaFileManager(current);
			replaceContextFileManager(context, intercepting);
			setField(javacFiler, "fileManager", intercepting);
			if (inNetBeansCompileOnSave(context)) {
				return InstallResult.success();
			}
			List<String> warnings = replaceNestedFileManagers(context, intercepting);
			return warnings.isEmpty()
				? InstallResult.success()
				: InstallResult.success(String.join("; ", warnings));
		} catch (ReflectiveOperationException ex) {
			return InstallResult.notInstalled("ConstExpr could not install stream interception: " + ex.getMessage());
		} catch (RuntimeException ex) {
			return InstallResult.notInstalled("ConstExpr stream interception failed: " + ex.getMessage());
		}
	}

	public static final class InstallResult {
		private final boolean installed;
		private final String message;

		private InstallResult(boolean installed, String message) {
			this.installed = installed;
			this.message = message;
		}

		public boolean installed() {
			return installed;
		}

		public String message() {
			return message;
		}

		private static InstallResult success() {
			return new InstallResult(true, null);
		}

		private static InstallResult success(String message) {
			return new InstallResult(true, message);
		}

		private static InstallResult notInstalled(String message) {
			return new InstallResult(false, message);
		}
	}

	private static void replaceContextFileManager(Object context, JavaFileManager fileManager)
		throws ReflectiveOperationException {
		Class<?> contextClass = context.getClass();
		Method keyMethod = contextClass.getDeclaredMethod("key", Class.class);
		ConstExprPermit.forceAccessible(keyMethod);
		Object key = keyMethod.invoke(context, JavaFileManager.class);

		Field htField = contextClass.getDeclaredField("ht");
		ConstExprPermit.forceAccessible(htField);
		@SuppressWarnings("unchecked")
		Map<Object, Object> ht = (Map<Object, Object>) htField.get(context);
		ht.put(key, fileManager);
	}

	private static List<String> replaceNestedFileManagers(Object context, JavaFileManager fileManager) {
		List<String> warnings = new ArrayList<>();
		try {
			Class<?> javaCompilerClass = Class.forName("com.sun.tools.javac.main.JavaCompiler");
			Method instance = javaCompilerClass.getMethod("instance", context.getClass());
			ConstExprPermit.forceAccessible(instance);
			Object compiler = instance.invoke(null, context);
			if (!setField(compiler, "fileManager", fileManager)) {
				warnings.add("javac JavaCompiler.fileManager field was not found");
			}
			if (javaSpecificationVersion() >= 26) {
				replaceClassWriterJdk26(context, compiler, fileManager);
			} else {
				replaceClassWriterFileManager(javaCompilerClass, compiler, fileManager);
			}
		} catch (ReflectiveOperationException ex) {
			warnings.add("ConstExpr could not replace nested javac file managers: " + ex.getMessage());
		}
		return warnings;
	}

	private static void replaceClassWriterFileManager(Class<?> javaCompilerClass,
	                                                  Object compiler,
	                                                  JavaFileManager fileManager)
		throws ReflectiveOperationException {
		Field writerField = findField(javaCompilerClass, "writer");
		if (writerField == null) {
			return;
		}
		ConstExprPermit.forceAccessible(writerField);
		Object writer = writerField.get(compiler);
		if (writer != null) {
			setField(writer, "fileManager", fileManager);
		}
	}

	private static void replaceClassWriterJdk26(Object context, Object compiler, JavaFileManager fileManager)
		throws ReflectiveOperationException {
		Class<?> classWriterClass = Class.forName("com.sun.tools.javac.jvm.ClassWriter");
		Field classWriterKeyField = findField(classWriterClass, "classWriterKey");
		if (classWriterKeyField == null) {
			replaceClassWriterFileManager(compiler.getClass(), compiler, fileManager);
			return;
		}

		ConstExprPermit.forceAccessible(classWriterKeyField);
		Object classWriterKey = classWriterKeyField.get(null);
		Class<?> contextKeyClass = Class.forName("com.sun.tools.javac.util.Context$Key");
		Method putKey = context.getClass().getMethod("put", contextKeyClass, Object.class);
		ConstExprPermit.forceAccessible(putKey);
		putKey.invoke(context, classWriterKey, null);

		Method instance = classWriterClass.getMethod("instance", context.getClass());
		ConstExprPermit.forceAccessible(instance);
		Object writer = instance.invoke(null, context);

		Method putClass = context.getClass().getMethod("put", Class.class, Object.class);
		ConstExprPermit.forceAccessible(putClass);
		putClass.invoke(context, classWriterClass, writer);
		setField(compiler, "writer", writer);
		setField(writer, "fileManager", fileManager);
	}

	private static boolean setField(Object target, String fieldName, Object value)
		throws ReflectiveOperationException {
		Field field = findField(target.getClass(), fieldName);
		if (field == null) {
			return false;
		}
		ConstExprPermit.forceAccessible(field);
		field.set(target, value);
		return true;
	}

	private static Field findField(Class<?> type, String fieldName) {
		for (Class<?> current = type; current != null; current = current.getSuperclass()) {
			try {
				return current.getDeclaredField(fieldName);
			} catch (NoSuchFieldException ignored) {
				// try superclass
			}
		}
		return null;
	}

	private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
		Method method = target.getClass().getDeclaredMethod(methodName);
		ConstExprPermit.forceAccessible(method);
		return method.invoke(target);
	}

	private static void stopJavacProcessingEnvironmentFromClosingProcessorClassLoader(Object javacEnv) {
		try {
			Field field = findField(javacEnv.getClass(), "processorClassLoader");
			if (field == null) {
				return;
			}
			ConstExprPermit.forceAccessible(field);
			ClassLoader processorClassLoader = (ClassLoader) field.get(javacEnv);
			if (processorClassLoader != null) {
				field.set(javacEnv, nonClosingClassLoader(processorClassLoader));
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// Best effort: some javac versions do not own or close the processor class loader.
		}
	}

	private static void forceMultipleRoundsInNetBeansEditor(Object javacEnv) {
		try {
			Field field = findField(javacEnv.getClass(), "isBackgroundCompilation");
			if (field != null) {
				ConstExprPermit.forceAccessible(field);
				field.set(javacEnv, true);
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// NetBeans-only field.
		}
	}

	private static void disablePartialReparseInNetBeansEditor(Object context) {
		try {
			Class<?> cancelServiceClass = Class.forName("com.sun.tools.javac.util.CancelService");
			Method instance = cancelServiceClass.getMethod("instance", context.getClass());
			ConstExprPermit.forceAccessible(instance);
			Object cancelService = instance.invoke(null, context);
			if (cancelService == null) {
				return;
			}
			Field parserField = findField(cancelService.getClass(), "parser");
			if (parserField == null) {
				return;
			}
			ConstExprPermit.forceAccessible(parserField);
			Object parser = parserField.get(cancelService);
			if (parser == null) {
				return;
			}
			Field supportsReparseField = findField(parser.getClass(), "supportsReparse");
			if (supportsReparseField != null) {
				ConstExprPermit.forceAccessible(supportsReparseField);
				supportsReparseField.set(parser, false);
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// NetBeans-only classes and fields.
		}
	}

	private static boolean inNetBeansCompileOnSave(Object context) {
		try {
			Class<?> optionsClass = Class.forName("com.sun.tools.javac.util.Options");
			Method instance = optionsClass.getMethod("instance", context.getClass());
			ConstExprPermit.forceAccessible(instance);
			Object options = instance.invoke(null, context);
			Method keySet = optionsClass.getMethod("keySet");
			ConstExprPermit.forceAccessible(keySet);
			Object keys = keySet.invoke(options);
			return keys instanceof Iterable<?> iterable
				&& contains(iterable, "ide")
				&& contains(iterable, "backgroundCompilation");
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return false;
		}
	}

	private static boolean contains(Iterable<?> iterable, String value) {
		for (Object item : iterable) {
			if (value.equals(String.valueOf(item))) {
				return true;
			}
		}
		return false;
	}

	private static ClassLoader nonClosingClassLoader(ClassLoader delegate) {
		return new ClassLoader(delegate) {
			@Override
			public Class<?> loadClass(String name) throws ClassNotFoundException {
				return delegate.loadClass(name);
			}

			@Override
			public URL getResource(String name) {
				return delegate.getResource(name);
			}

			@Override
			public Enumeration<URL> getResources(String name) throws IOException {
				return delegate.getResources(name);
			}

			@Override
			public InputStream getResourceAsStream(String name) {
				return delegate.getResourceAsStream(name);
			}

			@Override
			public void setDefaultAssertionStatus(boolean enabled) {
				delegate.setDefaultAssertionStatus(enabled);
			}

			@Override
			public void setPackageAssertionStatus(String packageName, boolean enabled) {
				delegate.setPackageAssertionStatus(packageName, enabled);
			}

			@Override
			public void setClassAssertionStatus(String className, boolean enabled) {
				delegate.setClassAssertionStatus(className, enabled);
			}

			@Override
			public void clearAssertionStatus() {
				delegate.clearAssertionStatus();
			}

			@Override
			public String toString() {
				return delegate.toString();
			}
		};
	}

	private static int javaSpecificationVersion() {
		String version = System.getProperty("java.specification.version", "8");
		if (version.startsWith("1.")) {
			version = version.substring(2);
		}
		int dot = version.indexOf('.');
		if (dot >= 0) {
			version = version.substring(0, dot);
		}
		try {
			return Integer.parseInt(version);
		} catch (NumberFormatException ex) {
			return 8;
		}
	}
}
