package io.github.connellite.constexpr.visitor;

interface AnnotationFoundListener<T> {
	void onFound(T descriptor);
}
