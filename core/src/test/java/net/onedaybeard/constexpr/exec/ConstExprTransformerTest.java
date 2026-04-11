package net.onedaybeard.constexpr.exec;

import net.onedaybeard.constexpr.AsmTransformException;
import net.onedaybeard.constexpr.AsmUtil;
import net.onedaybeard.constexpr.TestUtil;
import net.onedaybeard.constexpr.inspect.ClassMetadata;
import net.onedaybeard.constexpr.inspect.FieldDescriptor;
import net.onedaybeard.constexpr.lang.DynamicClassLoader;
import net.onedaybeard.constexpr.model.*;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static net.onedaybeard.constexpr.TestUtil.*;
import static org.junit.Assert.*;

public class ConstExprTransformerTest {

	@Test
	public void test_transform_simple() throws Exception {
		ClassMetadata scanA = TestUtil.scan(PlainPrimitive.class);

		byte[] transformed = transform(scanA);
		ClassMetadata scanB = TestUtil.scan(transformed);

		FieldDescriptor timestampA = scanA.field("timestamp");
		FieldDescriptor seedA = scanA.field("seed");

		FieldDescriptor timestampB = scanB.field("timestamp");
		FieldDescriptor seedB = scanB.field("seed");

		assertTrue(timestampA.isConstExpr);
		assertTrue(seedA.isConstExpr);
		assertNull(timestampA.value);
		assertNull(seedA.value);
		assertNotNull(method(scanA, "generateSeed"));

		assertFalse(timestampB.isConstExpr);
		assertFalse(seedB.isConstExpr);
		assertNotNull(timestampB.value);
		assertNotNull(seedB.value);
		assertNull(method(scanB, "generateSeed"));

		DynamicClassLoader dcl = new DynamicClassLoader();
		Class<?> tc = dcl.register(PlainPrimitive.class, transformed);

		long timestampA1 = readStaticField(PlainPrimitive.class, "timestamp");
		long timestampB1 = readStaticField(tc, "timestamp");
		Thread.sleep(50);
		long timestampA2 = readStaticField(PlainPrimitive.class, "timestamp");
		long timestampB2 = readStaticField(tc, "timestamp");

		assertEquals(timestampA1, timestampA2);
		assertEquals(timestampB1, timestampB2);

		Thread.sleep(50);

		DynamicClassLoader dcl2 = new DynamicClassLoader();
		Class<?> tc2 = dcl2.register(PlainPrimitive.class, transformed);
		long timestampC1 = readStaticField(tc2, "timestamp");
		assertEquals(timestampB1, timestampC1);
	}

	@Test
	public void test_transform_clinit_clean_PlainPrimitive() throws Exception {
		byte[] transformed = transform(PlainPrimitive.class);
		assertInsnCount(0, "<clinit>", AsmUtil.classReader(transformed));
	}

	@Test
	public void test_transform_clinit_clean_AlmostPlainString() throws Exception {
		byte[] transformed = transform(AlmostPlainPrimitive.class);
		// Count depends on javac output (non-constexpr static init left in <clinit>); adjust if JDK changes bytecode shape.
		assertInsnCount(20, "<clinit>", AsmUtil.classReader(transformed));
	}

	@Test
	public void test_transform_clinit_clean_PlainString() throws Exception {
		assertFalse(scan(PlainString.class).emptyClinit);

		byte[] transformed = transform(PlainString.class);
		assertInsnCount(0, "<clinit>", AsmUtil.classReader(transformed));
	}

	@Test
	public void test_transform_string() throws Exception {
		ClassMetadata scanA = TestUtil.scan(PlainString.class);

		byte[] transformed = transform(scanA);
		ClassMetadata scanB = TestUtil.scan(transformed);

		assertEquals(PlainString.s1, scanA.field("s1").value);
		assertNull(scanA.field("s2").value);

		assertEquals(PlainString.s1, scanB.field("s1").value);
		assertEquals(PlainString.s2, scanB.field("s2").value);
	}

	@Test
	public void transformer_returns_null_without_const_expr() throws Exception {
		ClassMetadata meta = scan(NoConstExpr.class);
		assertNull(new ConstExprTransformer(meta).call());
	}

	@Test
	public void transform_two_runtime_strings_embeds_both_and_strips_clinit() throws Exception {
		byte[] out = transform(TwoRuntimeStrings.class);
		assertInsnCount(0, "<clinit>", AsmUtil.classReader(out));

		ClassMetadata after = scan(out);
		assertEquals(TwoRuntimeStrings.first, after.field("first").value);
		assertEquals(TwoRuntimeStrings.second, after.field("second").value);

		DynamicClassLoader dcl = new DynamicClassLoader();
		Class<?> c = dcl.register(TwoRuntimeStrings.class, out);
		assertEquals(TwoRuntimeStrings.first, readStaticField(c, "first"));
		assertEquals(TwoRuntimeStrings.second, readStaticField(c, "second"));
	}

	@Test
	public void transform_int_and_runtime_string() throws Exception {
		byte[] out = transform(IntAndRuntimeString.class);
		TestUtil.verifyClass(out);
		ClassMetadata after = scan(out);
		assertEquals(Integer.valueOf(IntAndRuntimeString.magic), after.field("magic").value);
		assertEquals(IntAndRuntimeString.tag, after.field("tag").value);
		assertInsnCount(0, "<clinit>", AsmUtil.classReader(out));
	}

	@Test
	public void transform_string_builder_style_initializer() throws Exception {
		byte[] out = transform(StringFromStringBuilderPath.class);
		TestUtil.verifyClass(out);
		ClassMetadata after = scan(out);
		assertEquals(StringFromStringBuilderPath.built, after.field("built").value);
		assertInsnCount(0, "<clinit>", AsmUtil.classReader(out));
	}

	@Test
	public void transformed_plain_string_verifies_and_matches_runtime_type() throws Exception {
		byte[] out = transform(PlainString.class);
		DynamicClassLoader dcl = new DynamicClassLoader();
		Class<?> c = dcl.register(PlainString.class, out);
		assertEquals(PlainString.s1, readStaticField(c, "s1"));
		assertEquals(PlainString.s2, readStaticField(c, "s2"));
	}

	private static void assertInsnCount(int count, String method, ClassReader cr) {
		ClassNode cn = AsmUtil.classNode(cr);

		List<MethodNode> methods = cn.methods;
		MethodNode mn = methods.stream()
			.filter(m -> m.name.equals(method))
			.findFirst()
			.orElse(null);

		if (mn != null) {
			assertEquals(TestUtil.toString(mn), count, filterBodyNoDebug(mn).size());
		} else {
			assertEquals("not found: " + method, 0, count);
		}
	}

	private static byte[] transform(Class<?> type) throws Exception {
		return transform(TestUtil.scan(type));
	}

	private static byte[] transform(ClassMetadata scan) throws Exception {
		try {
			byte[] out = new ConstExprTransformer(scan).call();
			if (out != null) {
				TestUtil.verifyClass(out);
			}
			return out;
		} catch (Exception e) {
			throw new AsmTransformException(scan.classType, e);
		}
	}
}
