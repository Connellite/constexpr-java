package io.github.connellite.constexpr.visitor;

import io.github.connellite.constexpr.InvalidConstExprException;
import io.github.connellite.constexpr.model.*;
import io.github.connellite.constexpr.inspect.ClassMetadata;
import io.github.connellite.constexpr.inspect.FieldDescriptor;
import org.junit.Test;

import java.util.List;

import static io.github.connellite.constexpr.TestUtil.scan;
import static org.junit.Assert.*;

public class ConstExprScannerTest {
	@Test
	public void test_scan_simple() throws Exception {
		ClassMetadata scan = scan(PlainPrimitive.class);
		assertEquals(2, scan.fields.size());

		List<FieldDescriptor> constExprFields = scan.fields.stream()
			.filter(fd -> fd.isConstExpr)
			.toList();
		assertEquals(2, constExprFields.size());
		assertEquals("timestamp", constExprFields.get(0).name);
		assertEquals(1, scan.methods.stream()
			.filter(md -> md.isConstExpr)
			.count());
	}

	@Test
	public void test_scan_string() throws Exception {
		ClassMetadata scan = scan(PlainString.class);

		List<FieldDescriptor> fields = scan.fields;
		assertEquals(2, fields.size());
		assertEquals("s1", fields.get(0).name);
	}

	@Test
	public void test_scan_class_without_const_expr() throws Exception {
		ClassMetadata scan = scan(NoConstExpr.class);
		assertFalse(scan.containsConstExpr());
		assertEquals(0, scan.fields.stream().filter(f -> f.isConstExpr).count());
		assertEquals(0, scan.methods.stream().filter(m -> m.isConstExpr).count());
	}

	@Test
	public void test_scan_two_runtime_strings() throws Exception {
		ClassMetadata scan = scan(TwoRuntimeStrings.class);
		assertEquals(2, scan.fields.stream().filter(f -> f.isConstExpr && "Ljava/lang/String;".equals(f.desc)).count());
	}

	@Test
	public void test_scan_int_and_runtime_string() throws Exception {
		ClassMetadata scan = scan(IntAndRuntimeString.class);
		long constexprFields = scan.fields.stream().filter(f -> f.isConstExpr).count();
		assertEquals(2, constexprFields);
	}

	@Test(expected = InvalidConstExprException.class)
	public void test_fail_scan_annotated_non_static_field() throws Exception {
		scan(WrongFieldTypeA.class);
	}

	@Test(expected = InvalidConstExprException.class)
	public void test_fail_scan_object_field() throws Exception {
		scan(WrongFieldTypeB.class);
	}

	@Test(expected = InvalidConstExprException.class)
	public void test_fail_scan_instance_method() throws Exception {
		scan(WrongMethodTypeA.class);
	}
}
