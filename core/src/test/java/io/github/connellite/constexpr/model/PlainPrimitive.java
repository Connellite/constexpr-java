package io.github.connellite.constexpr.model;

import io.github.connellite.constexpr.ConstExpr;

import java.util.Random;

public class PlainPrimitive {
	@ConstExpr public static final long timestamp = System.currentTimeMillis();
	@ConstExpr public static final int seed = generateSeed();

	@ConstExpr
	private static int generateSeed() {
		String s = "hellooooo";
		int sum = 0;
		for (char c : s.toCharArray())
			sum += c;
		return new Random(sum).nextInt();
	}
}
