package io.github.connellite.constexpr.model;

import io.github.connellite.constexpr.ConstExpr;

public class PlainString {
	@ConstExpr public static final String s1 = "hmm" + "hoho";
	@ConstExpr public static final String s2 = "hmm" + hnn(0xDEAD) + "hoho";

	private static String hnn(int ignored) {
		return "-uh-";
	}
}
