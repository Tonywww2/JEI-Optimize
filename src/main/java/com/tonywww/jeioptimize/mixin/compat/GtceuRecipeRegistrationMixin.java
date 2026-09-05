package com.tonywww.jeioptimize.mixin.compat;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.integration.GtceuRecipeRegistrationBatcher;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeRegistration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Pseudo
@Mixin(targets = "com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeJEICategory", remap = false)
public abstract class GtceuRecipeRegistrationMixin {
    private static final Logger LOGGER = LogManager.getLogger(JeiOptimize.MOD_ID);

    @Redirect(
        method = "registerRecipes(Lmezz/jei/api/registration/IRecipeRegistration;)V",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;copyOf(Ljava/util/Collection;)Ljava/util/List;"
        ),
        require = 1
    )
    private static <T> List<T> jeiOptimize$deferLargeRecipeCopy(Collection<? extends T> recipes) {
        return GtceuRecipeRegistrationBatcher.prepare(recipes);
    }

    @Redirect(
        method = "registerRecipes(Lmezz/jei/api/registration/IRecipeRegistration;)V",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/api/registration/IRecipeRegistration;addRecipes(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V"
        ),
        require = 1
    )
    private static <T> void jeiOptimize$registerRecipesInBatches(
        IRecipeRegistration registration,
        RecipeType<T> recipeType,
        List<T> recipes
    ) {
        if (!GtceuRecipeRegistrationBatcher.isDeferredCopy(recipes)) {
            registration.addRecipes(recipeType, recipes);
            return;
        }

        int recipeCount = recipes.size();
        LOGGER.info(
            "JEI Optimize is registering GTCEu category {} in ordered batches ({} recipes, at most {} per batch).",
            recipeType.getUid(),
            recipeCount,
            GtceuRecipeRegistrationBatcher.BATCH_SIZE
        );
        long started = System.nanoTime();
        int batchCount = GtceuRecipeRegistrationBatcher.forEachBatch(
            recipes,
            batch -> registration.addRecipes(recipeType, batch)
        );
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        LOGGER.info(
            "JEI Optimize registered GTCEu category {}: {} recipes in {} ordered batches, {} ms.",
            recipeType.getUid(),
            recipeCount,
            batchCount,
            elapsedMillis
        );
    }
}