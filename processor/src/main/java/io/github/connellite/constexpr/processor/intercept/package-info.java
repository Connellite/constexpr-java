/**
 * Stream interception for {@code @ConstExpr} transformation during compilation.
 * <p>
 * This package implements the primary integration strategy, modeled after Project Lombok:
 * it replaces javac's {@link javax.tools.JavaFileManager} with a delegating wrapper that
 * buffers {@code .class} bytes on write and runs constexpr transformation before the bytes
 * reach disk.
 * <p>
 * Used when the annotation processor runs inside a real javac instance (IntelliJ JPS,
 * {@code JavaCompiler.getTask()}, command-line {@code javac}). {@link io.github.connellite.constexpr.processor.ConstExprProcessor}
 * tries to install this interceptor first in {@code init()}.
 *
 * @see <a href="https://github.com/projectlombok/lombok/blob/master/src/core/lombok/javac/apt/LombokProcessor.java#L153-L224">
 *     Lombok javac post-compile hook installation</a>
 * @see <a href="https://github.com/projectlombok/lombok/blob/master/src/core/lombok/javac/apt/InterceptingJavaFileManager.java">
 *     Lombok intercepting file manager</a>
 */
package io.github.connellite.constexpr.processor.intercept;
