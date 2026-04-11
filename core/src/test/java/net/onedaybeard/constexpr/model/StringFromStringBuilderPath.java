package net.onedaybeard.constexpr.model;

import net.onedaybeard.constexpr.ConstExpr;

/**
 * Forces a {@code StringBuilder}-style init (no string concat invokedynamic) for coverage of
 * {@code beginStringIndexOfLegacy}.
 */
public final class StringFromStringBuilderPath {
	@ConstExpr public static final String built = new StringBuilder("p").append("art").toString();
}
