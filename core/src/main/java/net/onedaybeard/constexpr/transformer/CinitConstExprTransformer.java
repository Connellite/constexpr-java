package net.onedaybeard.constexpr.transformer;


import net.onedaybeard.constexpr.AsmUtil;
import net.onedaybeard.constexpr.inspect.ClassMetadata;
import net.onedaybeard.constexpr.inspect.FieldDescriptor;
import net.onedaybeard.constexpr.inspect.MethodDescriptor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.*;
import java.util.stream.StreamSupport;

public class CinitConstExprTransformer extends MethodNode implements Opcodes {
	private final ClassMetadata metadata;
	private final MethodVisitor mv;

	private static final Class<?>[] INSN_NODE_DECREMENTORS = new Class[] {
		LdcInsnNode.class,
		MethodInsnNode.class
	};

	public CinitConstExprTransformer(ClassMetadata metadata, MethodDescriptor md, MethodVisitor mv) {
		super(AsmUtil.ASM_API, md.access, md.name, md.desc, md.signature, md.exceptions);
		this.metadata = metadata;
		this.mv = mv;
	}

	@Override
	public void visitEnd() {
		metadata.fields.stream()
			.filter(fd -> fd.isConstExpr && fd.value == null)
			.forEach(this::removeInitializer);

		Iterable<AbstractInsnNode> iterable = () -> instructions.iterator();
		metadata.emptyClinit = 1 == StreamSupport.stream(iterable.spliterator(), false)
			.filter(i -> !(i instanceof LabelNode))
			.filter(i -> !(i instanceof LineNumberNode))
			.filter(i -> !(i instanceof FrameNode))
			.count();

		accept(mv);
	}

	private void removeInitializer(FieldDescriptor fd) {
		int last = endIndexOf(fd);
		int begin = "Ljava/lang/String;".equals(fd.desc)
			? beginStringIndexOf(fd, last)
			: beginIndexOf(fd, last);
		AbstractInsnNode lastNode = instructions.get(last);

		ListIterator<AbstractInsnNode> it = instructions.iterator(begin);
		while (it.hasNext()) {
			AbstractInsnNode node = it.next();
			it.remove();

			if (node == lastNode)
				break;
		}
	}


	private int endIndexOf(FieldDescriptor fd) {
		for (AbstractInsnNode node : instructions) {
			if (node instanceof FieldInsnNode fin) {
				if (fd.name.equals(fin.name)) {
					return instructions.indexOf(fin);
				}
			}
		}

		throw new IllegalStateException(
			"No PUTSTATIC found in <clinit> for @ConstExpr field '" + fd.name + "' (" + fd.desc + ")");
	}

	private int beginIndexOf(FieldDescriptor fd, int endIndex) {
		int i = endIndex - 1;
		while (i >= 0) {
			AbstractInsnNode insn = instructions.get(i);
			if (insn instanceof InsnNode) {
				i--;
				continue;
			}
			if (isInsnNodeDecrementing(insn)) {
				return i;
			}
			i--;
		}
		throw new IllegalStateException(
			"Cannot find start of initializer for @ConstExpr field '" + fd.name + "' (expected LDC or invoke before PUTSTATIC)");
	}

	private int beginStringIndexOf(FieldDescriptor fd, int endIndex) {
		try {
			String owner = metadata.type.getInternalName();
			Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicInterpreter());
			Frame<BasicValue>[] frames = analyzer.analyze(owner, this);

			int k = frames[endIndex].getStackSize();
			for (int i = endIndex - 1; i >= 0; i--) {
				if (frames[i + 1].getStackSize() != k) {
					throw new IllegalStateException(
							"frame/stack mismatch at insn " + i + " before PUTSTATIC at " + endIndex);
				}
				k = frames[i].getStackSize();
				if (k == 0) {
					return i;
				}
			}
			throw new IllegalStateException("no instruction with empty stack before PUTSTATIC at " + endIndex);
		} catch (AnalyzerException | IllegalStateException e) {
			return beginStringIndexOfLegacy(fd, endIndex);
		}
	}

	private int beginStringIndexOfLegacy(FieldDescriptor fd, int endIndex) {
		int i = endIndex - 1;
		while (i >= 0) {
			AbstractInsnNode insn = instructions.get(i);
			if (insn instanceof TypeInsnNode tin) {
				if (NEW == tin.getOpcode() &&
					("java/lang/String".equals(tin.desc) || "java/lang/StringBuilder".equals(tin.desc))) {
					return i;
				}
			}
			i--;
		}

		throw new IllegalStateException(
			"Cannot find String/StringBuilder NEW for @ConstExpr field '" + fd.name + "' in <clinit>");
	}

	private static boolean isInsnNodeDecrementing(AbstractInsnNode insn) {
		return Arrays.stream(INSN_NODE_DECREMENTORS).anyMatch(t -> t == insn.getClass());
	}
}
