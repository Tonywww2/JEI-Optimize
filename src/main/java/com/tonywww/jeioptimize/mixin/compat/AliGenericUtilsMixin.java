package com.tonywww.jeioptimize.mixin.compat;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Pseudo
@Mixin(targets = "com.yanny.ali.compatibility.common.GenericUtils", remap = false)
public abstract class AliGenericUtilsMixin {
    @Redirect(
        method = "register",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;get(JLjava/util/concurrent/TimeUnit;)Ljava/lang/Object;"
        )
    )
    private static Object jeiopt$managedServerDataWait(
        CompletableFuture<?> future,
        long timeout,
        TimeUnit unit
    ) throws InterruptedException, ExecutionException, TimeoutException {
        if (!JeiOptFeatureFlags.enabled()) {
            return future.get(timeout, unit);
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            return future.get(timeout, unit);
        }

        long timeoutNanos = unit.toNanos(timeout);
        long deadline = System.nanoTime() + timeoutNanos;
        minecraft.managedBlock(() -> future.isDone() || System.nanoTime() - deadline >= 0L);

        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        if (!future.isDone()) {
            throw new TimeoutException();
        }
        return future.get();
    }
}
