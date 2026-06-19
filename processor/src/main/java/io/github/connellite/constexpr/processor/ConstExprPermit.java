package io.github.connellite.constexpr.processor;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Opens {@code jdk.compiler} packages to the processor without requiring user JVM flags.
 * <p>
 * Based on Project Lombok's JDK 16 module workaround:
 * <ul>
 *     <li>https://github.com/projectlombok/lombok/commit/9806e5cca4b449159ad0509dafde81951b8a8523</li>
 *     <li>https://github.com/projectlombok/lombok/blob/9806e5cca4b449159ad0509dafde81951b8a8523/src/core/lombok/javac/apt/LombokProcessor.java#L453-L484</li>
 * </ul>
 */
final class ConstExprPermit {
	private static final String[] JAVAC_PACKAGES = {
		"com.sun.tools.javac.api",
		"com.sun.tools.javac.code",
		"com.sun.tools.javac.comp",
		"com.sun.tools.javac.file",
		"com.sun.tools.javac.jvm",
		"com.sun.tools.javac.main",
		"com.sun.tools.javac.model",
		"com.sun.tools.javac.parser",
		"com.sun.tools.javac.processing",
		"com.sun.tools.javac.tree",
		"com.sun.tools.javac.util",
	};

	private ConstExprPermit() {}

	private static class Parent {
		boolean first;
	}

	static void openJavacCompilerPackages() {
		try {
			Class.forName("java.lang.Module");
		} catch (ClassNotFoundException e) {
			return;
		}
		openViaImplAddOpens();
		openViaInternalModules();
	}

	private static void openViaInternalModules() {
		try {
			Class<?> modules = Class.forName("jdk.internal.module.Modules");
			Method addOpens = modules.getDeclaredMethod("addOpens",
				Class.forName("java.lang.Module"), String.class, Class.forName("java.lang.Module"));
			overrideAccessible(unsafe(), addOpens);
			Object compilerModule = jdkCompilerModule();
			Object ownModule = ConstExprPermit.class.getModule();
			for (String pkg : JAVAC_PACKAGES) {
				addOpens.invoke(null, compilerModule, pkg, ownModule);
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// fall through
		}
	}

	private static void openViaImplAddOpens() {
		try {
			Object unsafe = unsafe();
			Object compilerModule = jdkCompilerModule();
			Object ownModule = ConstExprPermit.class.getModule();
			Class<?> moduleClass = Class.forName("java.lang.Module");
			Method implAddOpens = moduleClass.getDeclaredMethod("implAddOpens", String.class, moduleClass);
			overrideAccessible(unsafe, implAddOpens);
			for (String pkg : JAVAC_PACKAGES) {
				implAddOpens.invoke(compilerModule, pkg, ownModule);
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// fall through
		}
	}

	private static Object jdkCompilerModule() throws ReflectiveOperationException {
		Class<?> moduleLayerClass = Class.forName("java.lang.ModuleLayer");
		Method boot = moduleLayerClass.getMethod("boot");
		Object bootLayer = boot.invoke(null);
		Method findModule = moduleLayerClass.getMethod("findModule", String.class);
		Object optional = findModule.invoke(bootLayer, "jdk.compiler");
		return optional.getClass().getMethod("orElseThrow").invoke(optional);
	}

	private static Object unsafe() throws ReflectiveOperationException {
		Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
		Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
		theUnsafe.setAccessible(true);
		return theUnsafe.get(null);
	}

	private static void overrideAccessible(Object unsafe, AccessibleObject object) throws ReflectiveOperationException {
		Class<?> unsafeClass = unsafe.getClass();
		Method objectFieldOffset = unsafeClass.getMethod("objectFieldOffset", Field.class);
		Method putBooleanVolatile = unsafeClass.getMethod("putBooleanVolatile", Object.class, long.class, boolean.class);
		long offset = (long) objectFieldOffset.invoke(unsafe, Parent.class.getDeclaredField("first"));
		putBooleanVolatile.invoke(unsafe, object, offset, true);
	}

	static void forceAccessible(AccessibleObject object) throws ReflectiveOperationException {
		if (object.trySetAccessible()) {
			return;
		}
		overrideAccessible(unsafe(), object);
	}
}
