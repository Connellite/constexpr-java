package io.github.connellite.constexpr.processor;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.JavaFileManager;
import javax.tools.StandardJavaFileManager;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/**
 * Unwraps javac and IntelliJ JPS proxy wrappers around {@link ProcessingEnvironment} and {@link Filer},
 * following the same strategy as Project Lombok's {@code LombokProcessor}.
 *
 * @see <a href="https://github.com/projectlombok/lombok/blob/master/src/core/lombok/javac/apt/LombokProcessor.java#L416-L540">
 *     Lombok {@code getJavacProcessingEnvironment()} and {@code getJavacFiler()}</a>
 */
public final class ConstExprJavacUnwrap {
	private static final String JAVAC_PROCESSING_ENV =
		"com.sun.tools.javac.processing.JavacProcessingEnvironment";
	private static final String JAVAC_FILER =
		"com.sun.tools.javac.processing.JavacFiler";

	private ConstExprJavacUnwrap() {}

	public static Object javacProcessingEnvironment(ProcessingEnvironment processingEnv) {
		Object unwrapped = unwrap(processingEnv);
		return unwrapped != null && JAVAC_PROCESSING_ENV.equals(unwrapped.getClass().getName())
			? unwrapped
			: null;
	}

	public static Object javacFiler(Filer filer) {
		return unwrap(filer, JAVAC_FILER);
	}

	public static StandardJavaFileManager standardJavaFileManager(JavaFileManager fileManager) {
		return unwrap(fileManager, StandardJavaFileManager.class);
	}

	public static <T> T unwrap(Object instance, Class<T> targetType) {
		Object unwrapped = unwrapType(instance, targetType);
		return targetType.isInstance(unwrapped) ? targetType.cast(unwrapped) : null;
	}

	private static Object unwrap(ProcessingEnvironment processingEnv) {
		if (processingEnv == null) {
			return null;
		}
		if (JAVAC_PROCESSING_ENV.equals(processingEnv.getClass().getName())) {
			return processingEnv;
		}
		for (Class<?> type = processingEnv.getClass(); type != null; type = type.getSuperclass()) {
			Object delegate = tryField(type, processingEnv, "delegate");
			if (delegate == null) {
				delegate = tryProxyDelegate(processingEnv);
			}
			if (delegate == null) {
				delegate = tryField(type, processingEnv, "processingEnv");
			}
			if (delegate instanceof ProcessingEnvironment nested) {
				return unwrap(nested);
			}
			if (delegate != null) {
				Object javac = unwrap(delegate, JAVAC_PROCESSING_ENV);
				if (javac != null) {
					return javac;
				}
			}
		}
		return null;
	}

	private static Object unwrap(Filer filer, String targetTypeName) {
		if (filer == null) {
			return null;
		}
		if (targetTypeName.equals(filer.getClass().getName())) {
			return filer;
		}
		for (Class<?> type = filer.getClass(); type != null; type = type.getSuperclass()) {
			Object delegate = tryField(type, filer, "delegate");
			if (delegate == null) {
				delegate = tryProxyDelegate(filer);
			}
			if (delegate == null) {
				delegate = tryField(type, filer, "filer");
			}
			if (delegate != null) {
				Object match = unwrap(delegate, targetTypeName);
				if (match != null) {
					return match;
				}
			}
		}
		return null;
	}

	private static Object unwrap(Object instance, String targetTypeName) {
		if (instance == null) {
			return null;
		}
		if (targetTypeName.equals(instance.getClass().getName())) {
			return instance;
		}
		for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
			Object delegate = tryField(type, instance, "delegate");
			if (delegate == null) {
				delegate = tryProxyDelegate(instance);
			}
			if (delegate != null) {
				Object match = unwrap(delegate, targetTypeName);
				if (match != null) {
					return match;
				}
			}
		}
		return null;
	}

	private static Object unwrapType(Object instance, Class<?> targetType) {
		if (instance == null) {
			return null;
		}
		if (targetType.isInstance(instance)) {
			return instance;
		}
		for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
			Object delegate = tryField(type, instance, "fileManager");
			if (delegate == null) {
				delegate = tryField(type, instance, "delegate");
			}
			if (delegate == null) {
				delegate = tryField(type, instance, "clientJavaFileManager");
			}
			if (delegate == null) {
				delegate = tryField(type, instance, "wrapped");
			}
			if (delegate != null) {
				Object match = unwrapType(delegate, targetType);
				if (match != null) {
					return match;
				}
			}
		}
		return null;
	}

	private static Object tryProxyDelegate(Object instance) {
		if (!Proxy.isProxyClass(instance.getClass())) {
			return null;
		}
		try {
			InvocationHandler handler = Proxy.getInvocationHandler(instance);
			return tryField(handler.getClass(), handler, "val$delegateTo");
		} catch (Exception ignored) {
			return null;
		}
	}

	private static Object tryField(Class<?> type, Object instance, String fieldName) {
		try {
			Field field = type.getDeclaredField(fieldName);
			ConstExprPermit.forceAccessible(field);
			return field.get(instance);
		} catch (ReflectiveOperationException ignored) {
			return null;
		}
	}
}
