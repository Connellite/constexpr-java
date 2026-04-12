package io.github.connellite.constexpr.model;

import io.github.connellite.constexpr.ConstExpr;

/** Mix of primitive and runtime-built string in the same {@code <clinit>}. */
public final class IntAndRuntimeString {
	@ConstExpr public static final int magic = 3 * 7;
	@ConstExpr public static final String tag = "v" + version();

	private static String version() {
		return "1";
	}
}
