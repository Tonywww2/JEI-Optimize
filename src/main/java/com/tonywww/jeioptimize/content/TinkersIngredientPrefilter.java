package com.tonywww.jeioptimize.content;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TinkersIngredientPrefilter {
    static final List<String> DEFAULT_TAGS = List.of("tconstruct:modifiable", "tconstruct:parts");

    private TinkersIngredientPrefilter() {
    }

    public static List<ItemStack> apply(List<ItemStack> stacks) {
        if (!JeiOptFeatureFlags.prefilterTinkersIngredients() || stacks == null || stacks.isEmpty()) {
            return stacks;
        }

        try {
            List<TagKey<Item>> tags = parseTags(JeiOptFeatureFlags.tinkersIngredientFilterAdditionalTags());
            List<ItemStack> kept = new ArrayList<>(stacks.size());
            for (ItemStack stack : stacks) {
                if (!matchesAnyTag(stack, tags)) {
                    kept.add(stack);
                }
            }
            int removed = stacks.size() - kept.size();
            if (removed > 0) {
                JeiOptimize.LOGGER.warn(
                    "JEI Optimize prefiltered {} Tinkers item stack variants from JEI's global ingredient list using tags {}",
                    removed,
                    tags.stream().map(tag -> tag.location().toString()).toList()
                );
            }
            return List.copyOf(kept);
        } catch (RuntimeException | LinkageError e) {
            JeiOptimize.LOGGER.warn(
                "JEI Optimize could not apply the Tinkers ingredient prefilter; preserving JEI's original item list",
                e
            );
            return stacks;
        }
    }

    static List<TagKey<Item>> parseTags(String additionalTags) {
        List<TagKey<Item>> tags = new ArrayList<>();
        for (String entry : configuredTagIds(additionalTags)) {
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id == null) {
                JeiOptimize.LOGGER.warn("JEI Optimize ignored invalid Tinkers ingredient filter tag '{}'", entry);
                continue;
            }
            tags.add(TagKey.create(Registries.ITEM, id));
        }
        return List.copyOf(tags);
    }

    static List<String> configuredTagIds(String additionalTags) {
        Set<String> entries = new LinkedHashSet<>(DEFAULT_TAGS);
        if (additionalTags != null && !additionalTags.isBlank()) {
            for (String entry : additionalTags.split(",")) {
                String normalized = entry.trim().toLowerCase(Locale.ROOT);
                if (normalized.startsWith("#")) {
                    normalized = normalized.substring(1);
                }
                if (!normalized.isEmpty()) {
                    entries.add(normalized);
                }
            }
        }
        return List.copyOf(entries);
    }

    private static boolean matchesAnyTag(ItemStack stack, List<TagKey<Item>> tags) {
        for (TagKey<Item> tag : tags) {
            if (stack.is(tag)) {
                return true;
            }
        }
        return false;
    }
}