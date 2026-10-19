package com.tonywww.jeioptimize;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Ordinary classes must not reference a concrete Mixin implementation class.
 * Mixin accessor interfaces stay allowed.
 */
public final class TooltipMixinBoundaryTest {
    public static void main(String[] arguments) throws Exception {
        List<Path> roots = classRoots();
        for (String argument : arguments) {
            Path path = Path.of(argument);
            check(Files.exists(path), "built artifact exists: " + path);
            roots.add(path);
        }
        check(!roots.isEmpty(), "compiled classes or the built jar are available");
        Map<String, ClassNode> classes = new HashMap<>();
        for (Path root : roots) {
            readAll(root, classes);
        }
        Set<String> concreteMixins = new HashSet<>();
        Set<String> accessorInterfaces = new HashSet<>();
        for (ClassNode node : classes.values()) {
            if (!hasMixinAnnotation(node)) {
                continue;
            }
            if ((node.access & Opcodes.ACC_INTERFACE) != 0) {
                accessorInterfaces.add(node.name);
            } else {
                concreteMixins.add(node.name);
            }
        }
        List<String> violations = new ArrayList<>();
        for (ClassNode node : classes.values()) {
            if (concreteMixins.contains(node.name) || accessorInterfaces.contains(node.name)) {
                continue;
            }
            for (String referenced : references(node)) {
                if (concreteMixins.contains(referenced)) {
                    violations.add(node.name + " -> " + referenced);
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new AssertionError("ordinary class references a concrete mixin class: " + violations);
        }
        System.out.println("TooltipMixinBoundaryTest passed: concreteMixins=" + concreteMixins.size()
            + " accessorInterfaces=" + accessorInterfaces.size());
    }

    private static List<Path> classRoots() throws Exception {
        List<Path> roots = new ArrayList<>();
        Path cursor = Path.of("").toAbsolutePath();
        for (int step = 0; step < 6 && cursor != null; step++) {
            Path classes = cursor.resolve("build/classes/java/main");
            if (Files.isDirectory(classes)) {
                roots.add(classes);
            }
            cursor = cursor.getParent();
        }
        return roots;
    }

    private static void readAll(Path root, Map<String, ClassNode> classes) throws Exception {
        if (Files.isDirectory(root)) {
            try (var walk = Files.walk(root)) {
                for (Path path : walk.filter(file -> file.toString().endsWith(".class")).toList()) {
                    read(Files.newInputStream(path), classes);
                }
            }
            return;
        }
        try (ZipFile zip = new ZipFile(root.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    read(zip.getInputStream(entry), classes);
                }
            }
        }
    }

    private static void read(InputStream source, Map<String, ClassNode> classes) throws Exception {
        try (source) {
            ClassNode node = new ClassNode();
            new ClassReader(source).accept(node, 0);
            classes.put(node.name, node);
        }
    }

    private static boolean hasMixinAnnotation(ClassNode node) {
        return annotatedMixin(node.visibleAnnotations) || annotatedMixin(node.invisibleAnnotations);
    }

    private static boolean annotatedMixin(List<AnnotationNode> annotations) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationNode annotation : annotations) {
            if (annotation.desc != null && annotation.desc.contains("asm/mixin/Mixin")) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> references(ClassNode node) {
        Set<String> referenced = new HashSet<>();
        addType(referenced, node.superName);
        if (node.interfaces != null) {
            referenced.addAll(node.interfaces);
        }
        if (node.fields != null) {
            for (FieldNode field : node.fields) {
                addDescriptor(referenced, field.desc);
            }
        }
        if (node.methods != null) {
            for (MethodNode method : node.methods) {
                addDescriptor(referenced, method.desc);
                if (method.instructions == null) {
                    continue;
                }
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof TypeInsnNode type) {
                        addType(referenced, type.desc);
                    } else if (instruction instanceof FieldInsnNode field) {
                        addType(referenced, field.owner);
                    } else if (instruction instanceof MethodInsnNode call) {
                        addType(referenced, call.owner);
                    } else if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof org.objectweb.asm.Type type) {
                        addType(referenced, type.getInternalName());
                    } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                        addDescriptor(referenced, dynamic.desc);
                        if (dynamic.bsmArgs != null) {
                            for (Object argument : dynamic.bsmArgs) {
                                if (argument instanceof org.objectweb.asm.Handle handle) {
                                    addType(referenced, handle.getOwner());
                                }
                            }
                        }
                    }
                }
            }
        }
        return referenced;
    }

    private static void addDescriptor(Set<String> referenced, String descriptor) {
        if (descriptor == null) {
            return;
        }
        int cursor = 0;
        while (cursor < descriptor.length()) {
            int start = descriptor.indexOf('L', cursor);
            if (start < 0) {
                return;
            }
            int end = descriptor.indexOf(';', start);
            if (end < 0) {
                return;
            }
            addType(referenced, descriptor.substring(start + 1, end));
            cursor = end + 1;
        }
    }

    private static void addType(Set<String> referenced, String internalName) {
        if (internalName != null && !internalName.startsWith("[")) {
            referenced.add(internalName);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
