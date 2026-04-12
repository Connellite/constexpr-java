package io.github.connellite.constexpr.model;

import io.github.connellite.constexpr.ConstExpr;

public class WrongFieldTypeA {
	@ConstExpr
	public final long hmm = System.currentTimeMillis();
}
