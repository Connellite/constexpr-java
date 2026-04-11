package net.onedaybeard.constexpr;

import net.onedaybeard.constexpr.inspect.MethodDescriptor;
import org.junit.Test;

import static org.junit.Assert.*;

public class AsmUtilTest {

	@Test
	public void isStaticInitializer_true_for_clinit() {
		assertTrue(AsmUtil.isStaticInitializer("<clinit>", "()V"));
	}

	@Test
	public void isStaticInitializer_false_for_other_methods() {
		assertFalse(AsmUtil.isStaticInitializer("<init>", "()V"));
		assertFalse(AsmUtil.isStaticInitializer("foo", "()V"));
		assertFalse(AsmUtil.isStaticInitializer("<clinit>", "(I)V"));
	}

	@Test
	public void isStaticInitializer_accepts_method_descriptor() {
		MethodDescriptor md = new MethodDescriptor(
			0, "<clinit>", "()V", null, null);
		assertTrue(AsmUtil.isStaticInitializer(md));
	}
}
