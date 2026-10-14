package com.tonywww.jeioptimize;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The category-prep mixin applies only when JEI still has the visibility-cache members.
 * Forge 1.20.1 and NeoForge 1.21.1 both satisfy that contract today.
 */
public final class TooltipCategoryPrepContractTest {
    private static final String CLASS_NAME = "mezz/jei/library/recipes/RecipeManagerInternal.class";

    public static void main(String[] arguments) throws Exception {
        if (JeiOptMixinPlugin.hasCategoryVisibilityPrepContract(new ClassNode())) {
            throw new AssertionError("missing visibility members must disable category prep");
        }
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        try (InputStream stream = loader.getResourceAsStream(CLASS_NAME)) {
            if (stream == null) {
                throw new AssertionError("compile classpath has no RecipeManagerInternal");
            }
            assertContract(stream.readAllBytes(), "compile classpath");
        }
        for (String argument : arguments) {
            Path path = Path.of(argument);
            if (!Files.isRegularFile(path)) {
                throw new AssertionError("JEI jar is missing: " + path);
            }
            try (ZipFile zip = new ZipFile(path.toFile())) {
                ZipEntry entry = zip.getEntry(CLASS_NAME);
                if (entry == null) {
                    throw new AssertionError("RecipeManagerInternal is missing from " + path.getFileName());
                }
                try (InputStream stream = zip.getInputStream(entry)) {
                    assertContract(stream.readAllBytes(), path.getFileName().toString());
                }
            }
        }
        System.out.println("TooltipCategoryPrepContractTest passed");
    }

    private static void assertContract(byte[] bytes, String source) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
        if (!JeiOptMixinPlugin.hasCategoryVisibilityPrepContract(node)) {
            throw new AssertionError("category prep contract missing in " + source);
        }
        System.out.println("Category prep contract present: " + source);
    }
}
