package io.github.connellite.constexpr.processor;

import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes compiled output and compile classpath visible for reflective field resolution in {@code constexpr-core}.
 * <p>
 * Installs a temporary {@link URLClassLoader} as the thread context classloader for the duration of a
 * transformation and tears it down (restoring the previous classloader and closing the temporary one) on
 * {@link #close()}. Use with try-with-resources so the classloader and its file handles are never leaked.
 */
final class ConstExprClasspath implements AutoCloseable {
	private final ClassLoader previous;
	private final URLClassLoader created;

	private ConstExprClasspath(ClassLoader previous, URLClassLoader created) {
		this.previous = previous;
		this.created = created;
	}

	static ConstExprClasspath configure(StandardJavaFileManager fileManager) throws IOException {
		List<URL> urls = new ArrayList<>();
		addLocation(fileManager, StandardLocation.CLASS_PATH, urls);
		addLocation(fileManager, StandardLocation.CLASS_OUTPUT, urls);
		return install(urls);
	}

	static ConstExprClasspath configureForOutput(Path classOutput) throws IOException {
		List<URL> urls = new ArrayList<>();
		urls.add(toUrl(classOutput.toFile()));
		return install(urls);
	}

	@Override
	public void close() {
		Thread.currentThread().setContextClassLoader(previous);
		if (created != null) {
			try {
				created.close();
			} catch (IOException ignored) {
				// best-effort: nothing actionable if the temporary loader fails to close
			}
		}
	}

	private static ConstExprClasspath install(List<URL> urls) {
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		if (urls.isEmpty()) {
			return new ConstExprClasspath(previous, null);
		}
		URLClassLoader classLoader = URLClassLoader.newInstance(urls.toArray(URL[]::new), previous);
		Thread.currentThread().setContextClassLoader(classLoader);
		return new ConstExprClasspath(previous, classLoader);
	}

	private static void addLocation(StandardJavaFileManager fileManager,
	                                StandardLocation location,
	                                List<URL> urls) throws IOException {
		Iterable<? extends File> files = fileManager.getLocation(location);
		if (files == null) {
			return;
		}
		for (File file : files) {
			if (file != null) {
				urls.add(toUrl(file));
			}
		}
	}

	private static URL toUrl(File file) throws MalformedURLException {
		return file.toURI().toURL();
	}
}
