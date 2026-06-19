package io.github.connellite.constexpr.processor.intercept;

import io.github.connellite.constexpr.exec.ConstExprScannerTask;
import io.github.connellite.constexpr.inspect.ClassMetadata;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConstExprClassBytesTransformerTest {

	@Test
	public void transformsBufferedClassBytes() throws Exception {
		Path compiled = Path.of("../core/target/test-classes/io/github/connellite/constexpr/model/PlainString.class");
		assertTrue("core tests must be built first", Files.isRegularFile(compiled));

		byte[] original = Files.readAllBytes(compiled);
		List<File> classpath = List.of(compiled.getParent().getParent().getParent().getParent().getParent().toFile());

		byte[] transformed = ConstExprClassBytesTransformer.transformIfNeeded(
			original,
			"io.github.connellite.constexpr.model.PlainString",
			classpath
		);

		Path temp = Files.createTempFile("constexpr-bytes", ".class");
		try {
			Files.write(temp, transformed);
			ClassMetadata metadata = new ConstExprScannerTask(temp).call();
			assertFalse(metadata.fields.stream().filter(fd -> "s1".equals(fd.name)).findFirst().orElseThrow().isConstExpr);
		} finally {
			try {
				Files.deleteIfExists(temp);
			} catch (IOException ignored) {
				// Windows may keep the file locked briefly after class scanning.
			}
		}
	}
}
