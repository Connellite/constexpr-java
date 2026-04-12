package io.github.connellite.constexpr.model;

import io.github.connellite.constexpr.ConstExpr;

public class WrongFieldTypeB {
	@ConstExpr
	public static final Rectangle2D iDie = new Rectangle2D(0, 0, 0, 0);
}
