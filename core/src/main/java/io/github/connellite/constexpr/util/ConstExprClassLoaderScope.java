package io.github.connellite.constexpr.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Temporarily exposes build output and dependency entries through the thread context classloader.
 * <p>
 * {@code ConstExprFieldWeaver} reflectively reads static fields while transforming bytecode, so the
 * classes being transformed and their dependencies must be visible to the current thread.
 */
public final class ConstExprClassLoaderScope implements AutoCloseable {
	private final ClassLoader previous;
	private final URLClassLoader created;

	private ConstExprClassLoaderScope(ClassLoader previous, URLClassLoader created) {
		this.previous = previous;
		this.created = created;
	}

	public static ConstExprClassLoaderScope open(Iterable<? extends File> entries) throws IOException {
		List<URL> urls = new ArrayList<>();
		for (File entry : entries) {
			if (entry != null) {
				urls.add(entry.toURI().toURL());
			}
		}
		return openUrls(urls);
	}

	@Override
	public void close() {
		Thread.currentThread().setContextClassLoader(previous);
		if (created != null) {
			try {
				created.close();
			} catch (IOException ignored) {
				// Best effort: there is no useful recovery if a temporary classloader fails to close.
			}
		}
	}

	private static ConstExprClassLoaderScope openUrls(List<URL> urls) {
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		if (urls.isEmpty()) {
			return new ConstExprClassLoaderScope(previous, null);
		}
		URLClassLoader classLoader = URLClassLoader.newInstance(urls.toArray(URL[]::new), previous);
		Thread.currentThread().setContextClassLoader(classLoader);
		return new ConstExprClassLoaderScope(previous, classLoader);
	}
}
