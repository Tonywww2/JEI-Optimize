package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.index.AsyncSearchIndex;
import com.tonywww.jeioptimize.index.AsyncSearchIndexRegistry;
import com.tonywww.jeioptimize.index.SearchIndexBuilder;
import com.tonywww.jeioptimize.snapshot.IngredientSearchSnapshot;
import mezz.jei.core.search.PrefixInfo;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.ElementPrefixParser;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@Pseudo
@Mixin(targets = "mezz.jei.gui.search.ElementSearch", remap = false)
public abstract class ElementSearchMixin {
    @Shadow
    @Final
    private Map<Object, IListElement<?>> allElements;

    @Inject(method = "getSearchResults", at = @At("HEAD"), cancellable = true)
    private void jeiopt$getAsyncSearchResults(ElementPrefixParser.TokenInfo tokenInfo, CallbackInfoReturnable<Set<IListElement<?>>> callbackInfo) {
        if (!JeiOptFeatureFlags.searchPreheat()) {
            return;
        }

        AsyncSearchIndex asyncSearchIndex = AsyncSearchIndexRegistry.get(this);
        if (asyncSearchIndex == null) {
            return;
        }

        SearchIndexBuilder.SearchPrefix prefix = prefixFromTokenInfo(tokenInfo).orElse(null);
        String token = tokenInfo.token();
        if (prefix == null || token.isEmpty()) {
            return;
        }

        SearchIndexBuilder.BuiltSearchIndex index = asyncSearchIndex.readyValue()
            .orElseGet(() -> asyncSearchIndex.awaitOrFallback(() -> null));
        if (index == null) {
            return;
        }
        List<IngredientSearchSnapshot> snapshots = index.search(prefix, token);
        if (snapshots.isEmpty()) {
            callbackInfo.setReturnValue(Set.of());
            return;
        }

        Set<IListElement<?>> results = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IngredientSearchSnapshot snapshot : snapshots) {
            IListElement<?> element = allElements.get(snapshot.uid());
            if (element != null) {
                results.add(element);
            }
        }
        callbackInfo.setReturnValue(results);
    }

    private static Optional<SearchIndexBuilder.SearchPrefix> prefixFromTokenInfo(ElementPrefixParser.TokenInfo tokenInfo) {
        PrefixInfo<IListElementInfo<?>, IListElement<?>> prefixInfo = tokenInfo.prefixInfo();
        char character = prefixInfo.getPrefix();
        return switch (character) {
            case '\0' -> Optional.empty();
            case '@' -> Optional.of(SearchIndexBuilder.SearchPrefix.MOD);
            case '#' -> Optional.of(SearchIndexBuilder.SearchPrefix.TOOLTIP);
            case '$' -> Optional.of(SearchIndexBuilder.SearchPrefix.TAG);
            case '%' -> Optional.of(SearchIndexBuilder.SearchPrefix.CREATIVE_TAB);
            case '^' -> Optional.of(SearchIndexBuilder.SearchPrefix.COLOR);
            case '&' -> Optional.of(SearchIndexBuilder.SearchPrefix.RESOURCE_LOCATION);
            default -> Optional.empty();
        };
    }
}