package io.github.connellite.constexpr.model;

import io.github.connellite.constexpr.ConstExpr;

/**
 * Two {@code static final String} fields that need runtime bytecode (invokedynamic concat + calls),
 * so {@code <clinit>} must be sliced twice in declaration order.
 */
public final class TwoRuntimeStrings {
	@ConstExpr public static final String first = "a" + suffix(1);
	@ConstExpr public static final String second = "b" + suffix(2);

	private static String suffix(int i) {
		return "-" + i;
	}
}
