/**
 * Post-compile hooks for {@code @ConstExpr} transformation.
 * <p>
 * This package provides an independent safety net when stream interception is unavailable or insufficient.
 * It registers a javac {@link com.sun.source.util.TaskListener} on {@code COMPILATION} events
 * and, on the final annotation-processing round, scans the class output directory and runs
 * {@link io.github.connellite.constexpr.ConstExprMain} on compiled {@code .class} files.
 * <p>
 * The final annotation-processing pass is required for environments where javac does not expose
 * a hookable file manager or does not fire task events (for example, some IntelliJ JPS layouts).
 * {@link io.github.connellite.constexpr.processor.ConstExprProcessor} installs the task listener
 * alongside stream interception when javac exposes the required internals, and always runs the
 * post-compile pass on the last AP round.
 */
package io.github.connellite.constexpr.processor.hook;
