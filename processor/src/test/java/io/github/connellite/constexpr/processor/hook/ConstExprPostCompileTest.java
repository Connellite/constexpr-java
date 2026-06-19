package io.github.connellite.constexpr.processor.hook;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConstExprPostCompileTest {

	@Test
	public void normalizesIntelliJGeneratedSubdirectoryToModuleOutput() throws Exception {
		Path moduleOutput = Files.createTempDirectory("constexpr-module-output");
		Path generated = Files.createDirectories(moduleOutput.resolve("generated"));
		Path classFile = moduleOutput.resolve("Sample.class");
		Files.write(classFile, new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});

		Path normalized = ConstExprPostCompile.normalizeClassOutputDirForTest(generated);

		assertEquals(moduleOutput.toRealPath(), normalized);
		assertTrue(Files.exists(normalized.resolve("Sample.class")));
	}
}
