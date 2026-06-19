package io.github.connellite.constexpr.processor.intercept;

import io.github.connellite.constexpr.processor.ConstExprJavacUnwrap;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Intercepts {@code .class} output in the same way Project Lombok wraps javac's {@link JavaFileManager}.
 *
 * @see <a href="https://github.com/projectlombok/lombok/blob/master/src/core/lombok/javac/apt/InterceptingJavaFileManager.java">
 *     Lombok {@code InterceptingJavaFileManager}</a>
 */
final class ConstExprInterceptingJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
	ConstExprInterceptingJavaFileManager(JavaFileManager delegate) {
		super(delegate);
	}

	@Override
	public JavaFileObject getJavaFileForOutput(
		Location location,
		String className,
		JavaFileObject.Kind kind,
		FileObject sibling
	) throws IOException {
		JavaFileObject delegateFile = fileManager.getJavaFileForOutput(location, className, kind, sibling);
		if (kind != JavaFileObject.Kind.CLASS) {
			return delegateFile;
		}
		return new ConstExprInterceptingJavaFileObject(delegateFile, className, classpathEntries());
	}

	private List<File> classpathEntries() {
		List<File> entries = new ArrayList<>();
		StandardJavaFileManager standardFileManager = ConstExprJavacUnwrap.standardJavaFileManager(fileManager);
		if (standardFileManager != null) {
			addLocation(standardFileManager, StandardLocation.CLASS_PATH, entries);
			addLocation(standardFileManager, StandardLocation.CLASS_OUTPUT, entries);
			addLocation(standardFileManager, StandardLocation.ANNOTATION_PROCESSOR_PATH, entries);
		}
		return entries;
	}

	private static void addLocation(StandardJavaFileManager fileManager,
	                                JavaFileManager.Location location,
	                                List<File> entries) {
		try {
			Iterable<? extends File> files = fileManager.getLocation(location);
			if (files == null) {
				return;
			}
			for (File file : files) {
				if (file != null) {
					entries.add(file);
				}
			}
		} catch (Exception ignored) {
			// Location may be unsupported for this file manager implementation.
		}
	}
}
