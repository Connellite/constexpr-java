package io.github.connellite.constexpr.processor;

import io.github.connellite.constexpr.util.ConstExprClassLoaderScope;

import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes compiled output and compile classpath visible for reflective field resolution in {@code constexpr-core}.
 */
final class ConstExprClasspath {
	private ConstExprClasspath() {}

	static ConstExprClassLoaderScope configure(StandardJavaFileManager fileManager) throws IOException {
		List<File> entries = new ArrayList<>();
		addLocation(fileManager, StandardLocation.CLASS_PATH, entries);
		addLocation(fileManager, StandardLocation.CLASS_OUTPUT, entries);
		return ConstExprClassLoaderScope.open(entries);
	}

	static ConstExprClassLoaderScope configureForOutput(Path classOutput) throws IOException {
		return ConstExprClassLoaderScope.open(List.of(classOutput.toFile()));
	}

	private static void addLocation(StandardJavaFileManager fileManager,
	                                StandardLocation location,
	                                List<File> entries) {
		Iterable<? extends File> files = fileManager.getLocation(location);
		if (files == null) {
			return;
		}
		for (File file : files) {
			if (file != null) {
				entries.add(file);
			}
		}
	}
}
