package com.tonywww.jeioptimize.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

public final class TooltipMineColoniesAttributesTest {
    private record Modifier(UUID id, double amount, int operation) {}

    public static void main(String[] arguments) throws Exception {
        String plugin = "com.minecolonies.core.compatibility.jei.JEIPlugin";
        check(MineColoniesAttributeModifiers.shouldRepair(true, "Registering recipes", plugin), "MineColonies registration enabled");
        check(!MineColoniesAttributeModifiers.shouldRepair(false, "Registering recipes", plugin), "feature-off unchanged");
        check(!MineColoniesAttributeModifiers.shouldRepair(true, "Sending Runtime", plugin), "other phases unchanged");
        check(!MineColoniesAttributeModifiers.shouldRepair(true, "Registering recipes", "other.Plugin"), "other plugins unchanged");
        check(!MineColoniesAttributeModifiers.shouldRepair(true, null, null), "no scope unchanged");
        UUID shared = new UUID(0, 1);
        Modifier first = new Modifier(shared, 5, 0);
        Modifier other = new Modifier(new UUID(0, 2), 5, 0);
        Modifier replacement = new Modifier(shared, 0.25, 2);
        List<Modifier> input = new ArrayList<>(List.of(first, other, replacement));
        List<String> operations = new ArrayList<>();
        Consumer<Modifier> consumer = MineColoniesAttributeModifiers.equipmentConsumer(
            modifier -> operations.add("remove:" + modifier), modifier -> operations.add("add:" + modifier));
        input.forEach(consumer);
        check(operations.equals(List.of("remove:" + first, "add:" + first, "remove:" + other, "add:" + other,
            "remove:" + replacement, "add:" + replacement)), "original order and remove before every add");
        check(input.equals(List.of(first, other, replacement)), "source collection unchanged");
        operations.clear();
        List.<Modifier>of().forEach(consumer);
        check(operations.isEmpty(), "empty input unchanged");
        try {
            input.forEach(MineColoniesAttributeModifiers.equipmentConsumer(modifier -> {
                throw new IllegalArgumentException("broken remove");
            }, modifier -> { throw new AssertionError("add after failed removal"); }));
            throw new AssertionError("failure hidden");
        } catch (IllegalArgumentException expected) {}
        try {
            input.forEach(MineColoniesAttributeModifiers.equipmentConsumer(modifier -> {}, modifier -> {
                throw new IllegalArgumentException("broken add");
            }));
            throw new AssertionError("addition failure hidden");
        } catch (IllegalArgumentException expected) {}
        if (List.of(arguments).contains("--native-attributes")) {
            verifyMinecraftValues();
        }
        System.out.println("TooltipMineColoniesAttributesTest passed: equipment update order, scope, input unchanged, error propagation");
    }

    private static void verifyMinecraftValues() throws Exception {
        Class<?> attributeType = Class.forName("net.minecraft.world.entity.ai.attributes.Attribute");
        Class<?> rangedType = Class.forName("net.minecraft.world.entity.ai.attributes.RangedAttribute");
        Class<?> instanceType = Class.forName("net.minecraft.world.entity.ai.attributes.AttributeInstance");
        Class<?> modifierType = Class.forName("net.minecraft.world.entity.ai.attributes.AttributeModifier");
        Class<?> operationType = Class.forName("net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation");
        Object[] operations = operationType.getEnumConstants();
        var modifierConstructor = modifierType.getConstructor(UUID.class, String.class, double.class, operationType);
        var instanceConstructor = instanceType.getConstructor(attributeType, Consumer.class);
        var add = instanceType.getMethod("addTransientModifier", modifierType);
        var remove = instanceType.getMethod("removeModifier", modifierType);
        var value = instanceType.getMethod("getValue");
        Object attribute = rangedType.getConstructor(String.class, double.class, double.class, double.class)
            .newInstance("jet.test.attribute", 10.0, -1000.0, 1000.0);
        Consumer<Object> noop = ignored -> {};
        UUID shared = new UUID(0, 17);
        Object first = modifierConstructor.newInstance(shared, "first", 5.0, operations[0]);
        Object replacement = modifierConstructor.newInstance(shared, "replacement", 0.25, operations[2]);
        Object broken = instanceConstructor.newInstance(attribute, noop);
        add.invoke(broken, first);
        try {
            add.invoke(broken, replacement);
            throw new AssertionError("original duplicate did not reproduce");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            check(expected.getCause() instanceof IllegalArgumentException, "original failure is duplicate modifier");
        }
        Random random = new Random(4791065L);
        for (int trial = 0; trial < 200; trial++) {
            List<Object> modifiers = new ArrayList<>();
            modifiers.add(first);
            modifiers.add(replacement);
            for (int index = 0; index < 40; index++) {
                modifiers.add(modifierConstructor.newInstance(new UUID(0, random.nextInt(20)), "random",
                    (random.nextDouble() - 0.3) * (trial % 2 == 0 ? 0.5 : 100), operations[random.nextInt(3)]));
            }
            Object expected = instanceConstructor.newInstance(attribute, noop);
            for (Object modifier : modifiers) {
                remove.invoke(expected, modifier);
                add.invoke(expected, modifier);
            }
            Object actual = instanceConstructor.newInstance(attribute, noop);
            modifiers.forEach(MineColoniesAttributeModifiers.equipmentConsumer(modifier -> {
                try {
                    remove.invoke(actual, modifier);
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
            }, modifier -> {
                try {
                    add.invoke(actual, modifier);
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
            }));
            double expectedValue = (double) value.invoke(expected);
            double actualValue = (double) value.invoke(actual);
            check(Math.abs(expectedValue - actualValue) < 1e-9, "native attribute calculation differs at trial " + trial);
        }
        Object corrected = instanceConstructor.newInstance(attribute, noop);
        add.invoke(corrected, replacement);
        check((double) value.invoke(corrected) == 12.5, "base and multiply-total semantics preserved");
        System.out.println("Minecraft AttributeInstance differential passed: duplicate reproduced; 200 mixed-operation/clamped trials");
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}