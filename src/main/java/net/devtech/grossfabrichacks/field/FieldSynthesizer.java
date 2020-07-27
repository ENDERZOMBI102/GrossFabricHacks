package net.devtech.grossfabrichacks.field;

import java.util.List;
import net.devtech.grossfabrichacks.transformer.TransformerApi;
import net.devtech.grossfabrichacks.util.ASMUtil;
import net.devtech.grossfabrichacks.util.DelegatingInsnList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

public class FieldSynthesizer {
    private static final Logger LOGGER = LogManager.getLogger("GrossFabricHacks/FieldHandler");

    public static void registerTransformer() {
        TransformerApi.registerPreMixinAsmClassTransformer((final String name, final ClassNode klass) -> {
            try {
                transform(name, klass);
            } catch (final Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        });
        final StatefulImplementation interfase = new StatefulImplementationImpl();
//        final StatefulInterface test = new StatefulInterface() {};

//        final Class<?> klass = Class.forName(StatefulInterface.class.getName(), true, null);

/*
        LOGGER.info(interfase.getConsumer());
        LOGGER.info(interfase.getEnergy());
        LOGGER.info(interfase.getTest());

        for (int i = 0; i < 10; i++) {
            final Random random = new Random(i);

            interfase.setConsumer(j -> LOGGER.warn("generic hack" + random.nextLong() * j));
            interfase.setEnergy(random.nextLong());
            interfase.setTest(j -> LOGGER.warn("epic native Consumer<Integer> hack" + random.nextLong() * j));
        }

        LOGGER.info(interfase.getConsumer());
        LOGGER.info(interfase.getEnergy());
        LOGGER.info(interfase.getTest());

        interfase.getConsumer().accept(235897);
        interfase.getTest().accept(918629819);
*/
    }

    private static void transform(String name, ClassNode klass) {
        if (klass.visibleAnnotations != null) {
            for (final AnnotationNode annotation : klass.visibleAnnotations) {
                if ("Lnet/devtech/grossfabrichacks/field/Fields;".equals(annotation.desc)) {
                    //noinspection unchecked
                    for (final AnnotationNode field : (List<AnnotationNode>) ASMUtil.getAnnotationValue(annotation, "value")) {
                        addField(klass, field);
                    }
                }
            }
        }

        for (final MethodNode method : klass.methods) {
            if (method.visibleAnnotations != null) {
                for (final AnnotationNode annotation : method.visibleAnnotations) {
                    if (Type.getDescriptor(Getter.class).equals(annotation.desc)) {
                        FieldSynthesizer.get(name, method, annotation);
                    } else if (Type.getDescriptor(Setter.class).equals(annotation.desc)) {
                        FieldSynthesizer.set(name, method, annotation);
                    }
                }
            }
        }
    }

    private static void get(final String klass, final MethodNode method, final AnnotationNode annotation) {
        final String fieldDescriptor = Type.getReturnType(method.desc).getDescriptor();
        final String fieldName = ASMUtil.getAnnotationValue(annotation, "value");
        final DelegatingInsnList instructions = new DelegatingInsnList();

        instructions.addVarInsn(Opcodes.ALOAD, 0);
        instructions.addFieldInsn(Opcodes.GETFIELD, klass, fieldName, fieldDescriptor);

        if (FieldSynthesizer.insertAndSetAccess(method, annotation, instructions)) {
            instructions.addInsn(ASMUtil.getReturnOpcode(fieldDescriptor));

            method.instructions.insert(instructions);
        }
    }

    private static void set(final String klass, MethodNode method, final AnnotationNode annotation) {
        final String fieldDescriptor = ASMUtil.getExplicitParameters(method).get(0);
        final String fieldName = ASMUtil.getAnnotationValue(annotation, "value");
        final DelegatingInsnList instructions = new DelegatingInsnList();

        instructions.addVarInsn(Opcodes.ALOAD, 0);
        instructions.addVarInsn(ASMUtil.getLoadOpcode(fieldDescriptor), 1);
        instructions.addFieldInsn(Opcodes.PUTFIELD, klass, fieldName, fieldDescriptor);

        if (FieldSynthesizer.insertAndSetAccess(method, annotation, instructions)) {
            instructions.addInsn(ASMUtil.getReturnOpcode(method));

            method.instructions.insert(instructions);
        }
    }

    private static boolean insertAndSetAccess(final MethodNode method, final AnnotationNode annotation, final InsnList instructions) {
        final int access = ASMUtil.getAnnotationValue(annotation, "access", Setter.DEFAULT_ACCESS);
        final boolean override = ASMUtil.getAnnotationValue(annotation, "overrideAccess", true);

        boolean incomplete = true;

        for (final AbstractInsnNode instruction : method.instructions) {
            if (ASMUtil.isReturnOpcode(instruction.getOpcode())) {
                method.instructions.insertBefore(instruction, instructions);

                incomplete = false;
            }
        }

        if (access == Getter.DEFAULT_ACCESS) {
            if (override) {
                if (incomplete) {
                    method.access &= ~ASMUtil.ABSTRACT_ALL;
                }

                method.access |= Opcodes.ACC_SYNTHETIC;
            }
        } else {
            if (override) {
                method.access = access;
            } else {
                method.access |= access;
            }
        }

        return incomplete;
    }

    private static void addField(final ClassNode klass, AnnotationNode annotation) {
        final String name = ASMUtil.getAnnotationValue(annotation, "name");
        final String signature = ASMUtil.getAnnotationValue(annotation, "signature", Fields.Entry.NO_SIGNATURE);

        for (final FieldNode field : klass.fields) {
            if (field.name.equals(name)) {
                throw new RuntimeException(String.format("field %s already exists in %s.", name, klass.name));
            }
        }

        //noinspection StringEquality
        klass.fields.add(new FieldNode(ASMUtil.getAnnotationValue(annotation, "access", Fields.Entry.DEFAULT_ACCESS), name, ASMUtil.getAnnotationValue(annotation, "descriptor"), signature == Fields.Entry.NO_SIGNATURE ? null : signature, null));
    }
}
