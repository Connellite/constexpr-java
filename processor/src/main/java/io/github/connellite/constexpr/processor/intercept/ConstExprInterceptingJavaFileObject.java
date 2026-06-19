package io.github.connellite.constexpr.processor.intercept;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Buffers class bytes and runs constexpr when the compiler closes the output stream.
 *
 * @see <a href="https://github.com/projectlombok/lombok/blob/master/src/core/lombok/javac/apt/InterceptingJavaFileObject.java">
 *     Lombok {@code InterceptingJavaFileObject}</a>
 * @see <a href="https://github.com/projectlombok/lombok/blob/master/src/core/lombok/core/PostCompiler.java">
 *     Lombok {@code PostCompiler.wrapOutputStream()}</a>
 */
final class ConstExprInterceptingJavaFileObject implements JavaFileObject {
	private final JavaFileObject delegate;
	private final String className;
	private final List<java.io.File> classpath;

	ConstExprInterceptingJavaFileObject(JavaFileObject delegate, String className, List<java.io.File> classpath) {
		this.delegate = delegate;
		this.className = className;
		this.classpath = classpath;
	}

	@Override
	public OutputStream openOutputStream() throws IOException {
		OutputStream original = delegate.openOutputStream();
		AtomicBoolean closed = new AtomicBoolean();
		java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();

		return new OutputStream() {
			@Override
			public void write(int b) {
				buffer.write(b);
			}

			@Override
			public void write(byte[] bytes, int off, int len) {
				buffer.write(bytes, off, len);
			}

			@Override
			public void close() throws IOException {
				if (closed.getAndSet(true)) {
					original.close();
					return;
				}
				byte[] payload = buffer.toByteArray();
				payload = ConstExprClassBytesTransformer.transformIfNeeded(payload, className, classpath);
				original.write(payload);
				original.close();
			}
		};
	}

	@Override
	public Writer openWriter() throws IOException {
		throw new UnsupportedOperationException("Class files must be written through an OutputStream");
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ConstExprInterceptingJavaFileObject other)) {
			return false;
		}
		return className.equals(other.className) && delegate.equals(other.delegate);
	}

	@Override
	public int hashCode() {
		return className.hashCode() ^ delegate.hashCode();
	}

	@Override
	public boolean delete() {
		return delegate.delete();
	}

	@Override
	public Modifier getAccessLevel() {
		return delegate.getAccessLevel();
	}

	@Override
	public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
		return delegate.getCharContent(ignoreEncodingErrors);
	}

	@Override
	public Kind getKind() {
		return delegate.getKind();
	}

	@Override
	public long getLastModified() {
		return delegate.getLastModified();
	}

	@Override
	public String getName() {
		return delegate.getName();
	}

	@Override
	public NestingKind getNestingKind() {
		return delegate.getNestingKind();
	}

	@Override
	public boolean isNameCompatible(String simpleName, Kind kind) {
		return delegate.isNameCompatible(simpleName, kind);
	}

	@Override
	public InputStream openInputStream() throws IOException {
		return delegate.openInputStream();
	}

	@Override
	public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
		return delegate.openReader(ignoreEncodingErrors);
	}

	@Override
	public URI toUri() {
		return delegate.toUri();
	}

	@Override
	public String toString() {
		return delegate.toString();
	}
}
