package com.tonywww.jeioptimize;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.MixinService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skips a mixin when the JEI member it patches is missing or has a different descriptor, so a JEI
 * update switches the affected optimization off instead of crashing the game.
 *
 * <p>Mixin treats a callback descriptor mismatch or a missing {@code @Shadow} member as a fatal
 * error that {@code require = 0} does not suppress, so the check has to run before the mixin is
 * applied. Everything not listed here is left to Mixin's own (now non-fatal) target resolution.
 */
public final class JeiOptMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("jei_optimize");
    private static final String MIXIN_PACKAGE = "com.tonywww.jeioptimize.mixin.";

    private static final Map<String, Requirement> REQUIREMENTS = Map.of(
        MIXIN_PACKAGE + "IngredientFilterMixin", Requirement.method(
            "async ingredient filter",
            "<init>",
            "(Lmezz/jei/gui/filter/IFilterTextSource;"
                + "Lmezz/jei/common/config/IClientConfig;"
                + "Lmezz/jei/common/config/IIngredientFilterConfig;"
                + "Lmezz/jei/api/runtime/IIngredientManager;"
                + "Ljava/util/Comparator;"
                + "Ljava/util/List;"
                + "Lmezz/jei/api/helpers/IModIdHelper;"
                + "Lmezz/jei/api/runtime/IIngredientVisibility;"
                + "Lmezz/jei/api/helpers/IColorHelper;"
                + "Lmezz/jei/common/config/IClientToggleState;)V"),
        MIXIN_PACKAGE + "AnvilRecipeControlMixin", Requirement.method(
            "anvil recipe hiding",
            "getRepairRecipes",
            "(Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;"
                + "Lmezz/jei/api/ingredients/IIngredientHelper;)Ljava/util/stream/Stream;"),
        MIXIN_PACKAGE + "ElementSearchMixin", Requirement.field(
            "async search preheat",
            "allElements",
            "Ljava/util/Map;"),
        MIXIN_PACKAGE + "RecipeManagerInternalCompactMixin", Requirement.method(
            "delayed recipe list compaction",
            "compact",
            "()V"),
        MIXIN_PACKAGE + "VanillaRecipesMixin", Requirement.field(
            "recipe ingredient pre-resolve",
            "recipeManager",
            null),
        MIXIN_PACKAGE + "IngredientFilterModernMixin", Requirement.method(
            "async ingredient filter",
            "createElementSearch",
            "(Lmezz/jei/common/config/IClientConfig;"
                + "Lmezz/jei/gui/search/ElementPrefixParser;"
                + "Ljava/util/List;"
                + "Lmezz/jei/api/runtime/IIngredientManager;)"
                + "Lmezz/jei/gui/search/IElementSearch;"),
        MIXIN_PACKAGE + "ItemStackListFactoryMixin", Requirement.method(
            "creative tab skipping",
            "create",
            "(Lmezz/jei/common/util/StackHelper;)Ljava/util/List;")
    );

    private final Map<String, ClassNode> targetCache = new HashMap<>();

    /** Types a mixin names in its own signatures; Mixin fails hard if one of them has moved. */
    private static final Map<String, RequiredClass> REQUIRED_CLASSES = Map.of(
        MIXIN_PACKAGE + "ElementSearchMixin",
        new RequiredClass("async search preheat", "mezz.jei.core.search.PrefixInfo")
    );

    /** One of these covers each JEI generation, so the one that does not match is not a problem. */
    private static final Set<String> VARIANTS = Set.of(
        MIXIN_PACKAGE + "IngredientFilterMixin",
        MIXIN_PACKAGE + "IngredientFilterModernMixin"
    );

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        RequiredClass requiredClass = REQUIRED_CLASSES.get(mixinClassName);
        if (requiredClass != null && readTarget(requiredClass.className()) == null) {
            LOGGER.warn(
                "JEI Optimize turned off its {} optimization: this JEI build no longer has {}. "
                    + "JEI keeps its normal behavior; the mod needs an update for this JEI version.",
                requiredClass.feature(), requiredClass.className());
            return false;
        }

        Requirement requirement = REQUIREMENTS.get(mixinClassName);
        if (requirement == null) {
            return true;
        }

        ClassNode target = readTarget(targetClassName);
        if (target == null) {
            LOGGER.warn(
                "JEI Optimize could not read {}, so its {} optimization stays off and JEI keeps its normal behavior.",
                targetClassName, requirement.feature());
            return false;
        }
        if (requirement.isPresentIn(target)) {
            return true;
        }

        if (VARIANTS.contains(mixinClassName)) {
            LOGGER.debug(
                "JEI Optimize skipped {}: this JEI build's {} does not declare {}; another variant should cover it.",
                mixinClassName, targetClassName, requirement.describe());
            return false;
        }

        LOGGER.warn(
            "JEI Optimize turned off its {} optimization: this JEI build's {} no longer declares {}. "
                + "JEI keeps its normal behavior; the mod needs an update for this JEI version.",
            requirement.feature(), targetClassName, requirement.describe());
        return false;
    }

    private ClassNode readTarget(String targetClassName) {
        if (targetCache.containsKey(targetClassName)) {
            return targetCache.get(targetClassName);
        }
        ClassNode node = null;
        try {
            IClassBytecodeProvider provider = MixinService.getService().getBytecodeProvider();
            try {
                node = provider.getClassNode(targetClassName);
            } catch (ClassNotFoundException e) {
                node = provider.getClassNode(targetClassName.replace('.', '/'));
            }
        } catch (Exception | LinkageError e) {
            LOGGER.debug("JEI Optimize could not read target class {}", targetClassName, e);
        }
        targetCache.put(targetClassName, node);
        return node;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private record RequiredClass(String feature, String className) {
    }

    private record Requirement(String feature, String memberName, String descriptor, boolean isField) {
        static Requirement method(String feature, String name, String descriptor) {
            return new Requirement(feature, name, descriptor, false);
        }

        /** A null descriptor matches on name alone, for members whose type is remapped at runtime. */
        static Requirement field(String feature, String name, String descriptor) {
            return new Requirement(feature, name, descriptor, true);
        }

        boolean isPresentIn(ClassNode target) {
            if (isField) {
                for (FieldNode field : target.fields) {
                    if (memberName.equals(field.name) && (descriptor == null || descriptor.equals(field.desc))) {
                        return true;
                    }
                }
                return false;
            }
            for (MethodNode method : target.methods) {
                if (memberName.equals(method.name) && descriptor.equals(method.desc)) {
                    return true;
                }
            }
            return false;
        }

        String describe() {
            return (isField ? "field " : "method ") + memberName + (descriptor == null ? "" : descriptor);
        }
    }
}
