package io.github.connellite.constexpr;

import io.github.connellite.constexpr.inspect.FieldDescriptor;
import io.github.connellite.constexpr.inspect.MethodDescriptor;

public class InvalidConstExprException extends RuntimeException {
	public InvalidConstExprException(String message, FieldDescriptor descriptor) {
		super(message + ": " + descriptor);
	}

	public InvalidConstExprException(String message, MethodDescriptor descriptor) {
		super(message + ": " + descriptor);
	}
}
