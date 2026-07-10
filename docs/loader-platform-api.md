# 参考：平台 API 差异（Forge 1.20.1 ↔ NeoForge 1.21.1，reloadonlydata 版）

> 用途：指导 reloadonlydata 在两个加载器/版本上的 Stonecutter 条件实现。
> 核实来源：Forge 1.20.1 官方 patch（`RecipeManager`/`PlayerList`，第一手核实）、KubeJS `2001`（6 代/Forge）与 `2101`（7 代/NeoForge）真实源码。标「待落地验证」处以编译器/反编译源码为准。
> Stonecutter 注释语法见 [stonecutter.md](stonecutter.md)；构建见 [multiloader-build.md](multiloader-build.md)。

> ⚠️ 本项目**不需要**方块/方块实体/物品注册、客户端渲染、方块实体同步、自定义网络包、datagen。以下只覆盖 reloadonlydata 真正用到的平台边界。

目录：
1. Mod 入口与命令注册
2. `RecipeManager` 结构与 `apply`（Mixin `@Invoker`）
3. 配方同步（`ClientboundUpdateRecipesPacket`）
4. 条件配方（两版都在 `apply` 内自动处理）
5. 干净资源管理器（`PackRepository.openAllSelected`）
6. KubeJS 兼容差异（6 vs 7，已核实）
7. 映射到 reloadonlydata / 隔离清单

---

## 1. Mod 入口与命令注册

| 项 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| `@Mod` / 事件总线 | `net.minecraftforge.fml.common.Mod`、`FMLJavaModLoadingContext` | `net.neoforged.fml.common.Mod`、构造器注入 `IEventBus` |
| 命令注册事件 | `net.minecraftforge.event.RegisterCommandsEvent` | `net.neoforged.neoforge.event.RegisterCommandsEvent` |
| 取 dispatcher | `event.getDispatcher()` | `event.getDispatcher()` |
| 事件总线 | game/forge 总线 | game/neoforge 总线 |

- `RegisterCommandsEvent` 两版**类名相同、方法相同**，仅**包名不同** → 用 `//? if forge {` 隔离 import 即可，注册逻辑一致：

```java
//? if forge {
import net.minecraftforge.event.RegisterCommandsEvent;
//?} else {
/*import net.neoforged.neoforge.event.RegisterCommandsEvent;
*///?}

@SubscribeEvent
public void onRegisterCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("reloadrecipes")
        .requires(s -> s.hasPermission(2))
        .executes(ctx -> { /* ... */ return 1; }));
}
```

- 命令是**服务端**逻辑；订阅挂 game 总线。两版订阅注册方式的细节（`@EventBusSubscriber` 目标总线）按各自加载器约定，命令体本身两版一致。

---

## 2. `RecipeManager` 结构与 `apply`（Mixin `@Invoker`）

**核实到的核心差异**（1.21 引入 `RecipeHolder<?>` 包装）：

| 项 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| `apply` 签名 | `apply(Map<ResourceLocation, JsonElement>, ResourceManager, ProfilerFiller)` | **相同** |
| `byName` 字段 | `Map<ResourceLocation, Recipe<?>>` | `Map<ResourceLocation, RecipeHolder<?>>` |
| 按类型存储 | `Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes` | `Multimap<RecipeType<?>, RecipeHolder<?>> byType` |
| `getRecipes()` 返回 | `Collection<Recipe<?>>` | `Collection<RecipeHolder<?>>` |
| 配方对象 | `Recipe<?>` | `RecipeHolder<?>`（= record(id, Recipe)）|

> `apply` 是 `protected`；两版方法名同为 `apply`、签名一致 → **Mixin `@Invoker` 两版共用同一接口**（无需 Stonecutter 隔离；Loom refmap 在 Forge 运行时映射到 SRG、NeoForge 用 Mojmap）：

```java
@Mixin(RecipeManager.class)
public interface RecipeManagerInvoker {
    @Invoker("apply")
    void reloadonlydata$invokeApply(
        Map<ResourceLocation, JsonElement> map,
        ResourceManager resourceManager,
        ProfilerFiller profiler);
}
```

- 调用：`((RecipeManagerInvoker) recipeManager).reloadonlydata$invokeApply(map, rm, InactiveProfiler.INSTANCE);`
- 扫描 `recipes` 目录得到 `Map<ResourceLocation, JsonElement>` 的代码（`FileToIdConverter.json("recipes")` + `GsonHelper.fromJson`）**两版一致**。
- 我们**不直接触碰** `Recipe<?>` / `RecipeHolder<?>`（只传 JSON map 给 `apply`，让平台自己重建），故 §2 的类型差异**基本不进入我们的代码**——除非要遍历/统计配方数（`recipeManager.getRecipes().size()` 两版都可，无需隔离）。

---

## 3. 配方同步（`ClientboundUpdateRecipesPacket`）

已核实 Forge 1.20.1 patch：`PlayerList` 用 `new ClientboundUpdateRecipesPacket(server.getRecipeManager().getRecipes())` 下发。

| 项 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| 构造参数 | `Collection<Recipe<?>>` | `Collection<RecipeHolder<?>>` |
| 写法 | `new ClientboundUpdateRecipesPacket(recipeManager.getRecipes())` | **相同写法**（`getRecipes()` 返回类型不同但各自匹配构造） |
| 发送 | `player.connection.send(packet)` | **相同** |
| 配方书 | `player.getRecipeBook().sendInitialRecipeBook(player)` | **相同**（`sendInitialRecipeBook` 两版都在）|

> **同步代码两版一致**，无需 Stonecutter 隔离：

```java
var packet = new ClientboundUpdateRecipesPacket(recipeManager.getRecipes());
for (ServerPlayer p : server.getPlayerList().getPlayers()) {
    p.connection.send(packet);
    p.getRecipeBook().sendInitialRecipeBook(p);
}
```

> **JEI/REI 刷新需 tags + recipes 两个包**（已核实 JEI `StartEventObserver`）：JEI 要求同一周期内同时收到 `TagsUpdatedEvent` + `RecipesUpdatedEvent` 才 restart 重载配方显示，只发 recipes 包不刷新。故 `RecipeSync` 额外下发一个 tags 包（内容不变）。
>
> **tags 包双版本差异**（1.20.5 网络重构把 tags 包从 `game` 移到 `common`）：
>
> | 项 | Forge 1.20.1 | NeoForge 1.21.1 |
> | --- | --- | --- |
> | `ClientboundUpdateTagsPacket` 包 | `net.minecraft.network.protocol.game` | `net.minecraft.network.protocol.common` |
> | `TagNetworkSerialization.serializeTagsToNetwork(server.registries())` + 构造 | 相同写法 | 相同写法 |
>
> 仅 import 包路径需 Stonecutter `//? if` 隔离；构造与序列化调用两版一致（均已编译验证）。

---

## 4. 条件配方（两版都在 `apply` 内自动处理）

| 项 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| 机制 | `CraftingHelper.processConditions`（Forge patch 进 `apply`，条件 `forge:...`）| 原生 `ICondition` + Codec（`neoforge:conditions`，进 `apply` 的 Codec 解码）|
| 我们要做的 | **无** | **无** |

> 关键：两版我们都**调实例的 `apply`**，条件配方由各自平台在 `apply` 内部处理。reloadonlydata **无需感知**条件系统差异——这也是「必须调实例 apply、不能自己手写 JSON 解析」的核心原因。

---

## 5. 干净资源管理器（`PackRepository.openAllSelected`）

用于 KubeJS 兼容时重建「不含 KubeJS 虚拟数据包」的资源管理器（见 §6）。两版 API 一致：

```java
CloseableResourceManager clean = new MultiPackResourceManager(
    PackType.SERVER_DATA, server.getPackRepository().openAllSelected());
// 用完 close() 释放句柄
```

- `MinecraftServer#getPackRepository()`、`PackRepository#openAllSelected()`、`MultiPackResourceManager(PackType, List)`——1.20.1 与 1.21.1 **相同**，无需隔离。

---

## 6. KubeJS 兼容差异（6 vs 7，已核实源码）

**两代 API 完全不同，兼容层必须版本化**（整段 `//? if forge {`/`//? if neoforge {` 或两个平级实现类，放 `compat/kubejs/`）。

### 6.1 KubeJS 6（分支 `2001`，Forge 1.20.1）— 已核实

- `RecipeManagerMixin`：`@Inject(method="apply*", at=@At("HEAD"), cancellable=true)`。开关是**静态字段** `RecipesEventJS.instance`：非 null → `instance.post(recipeManager, map)` 处理配方 → `instance=null` → **`ci.cancel()`** 吃掉原版。
- `RecipesEventJS.instance` 在 `ServerScriptManager.wrapResourceManager()` **末尾**设置（重跑 server_scripts 之后）。
- 收尾：`KubeJSReloadListener.postAfterRecipes()`（**静态方法**）触发 `RECIPES_AFTER_LOADED`。
- **兼容流程**：
  1. 干净 RM（§5）→ `ServerScriptManager.instance.wrapResourceManager(clean)`（重跑脚本 + 设 `instance`，返回 wrapped RM）
  2. 用 wrapped RM 扫描 recipes → map
  3. `((RecipeManagerInvoker) rm).invokeApply(map, wrapped, profiler)` → KubeJS mixin 在 HEAD 接管并 cancel
  4. `KubeJSReloadListener.postAfterRecipes()`
  5. 同步客户端（§3）

### 6.2 KubeJS 7（分支 `2101`，NeoForge 1.21.1）— 已核实

- `RecipeManagerMixin implements RecipeManagerKJS`：
  - `@Inject apply @At("HEAD")`（**无 cancellable，不 cancel**）：`ServerEvents.RECIPES.hasListeners()` 时 `kjs$event = new RecipesKubeEvent(manager, resourceManager); kjs$event.post(this, map)`。
  - `@Inject apply @At("TAIL")`：`kjs$event.finishEvent()`（用 KubeJS 结果覆盖 vanilla 的 `byName`/`byType`），`kjs$event = null`。
  - 中途 `@Inject` 在 vanilla 的 `LOGGER.error` 处捕获被改坏的失败配方（容错，不刷屏）。
  - duck 接口 `RecipeManagerKJS`：`kjs$getResources()/kjs$setResources()`、`kjs$replaceRecipes(Map<ResourceLocation, RecipeHolder<?>>)`；`@Shadow byName: Map<ResourceLocation, RecipeHolder<?>>`、`byType: Multimap<RecipeType<?>, RecipeHolder<?>>`。
- `kjs$resources`（`@Unique` 实例字段）在**每次 `ReloadableServerResources` 构造时**经 `recipes.kjs$setResources(this)` 设置（`ReloadableServerResourcesMixin.<init>` RETURN，已核实）；指向当前 serverResources，**复用现有 RecipeManager 时持久有效**。
- `KubeJSReloadListener` 变成 **`ResourceManagerReloadListener` record**（`RECIPES_AFTER_LOADED` 经资源重载触发，**无静态 `postAfterRecipes()`**）。
- **兼容要点（R2 已核实 — PA-3/Agent3，源码分支 `2101`）**：
  - 因 `kjs$resources` 复用有效、apply 注入**不 cancel**（容错设计），我们调 `invokeApply` 时 KubeJS 会**自然介入**（HEAD 处理 + TAIL 覆盖）。
  - **重跑 server_scripts 的公开入口**：`((RecipeManagerKJS) rm).kjs$getResources().kjs$getServerScriptManager().reload()`——`ServerScriptManager.reload()`（public）重读 `.js`、重注册 `ServerEvents.RECIPES`、更新虚拟数据包内容。（`ServerScriptManager.createPackResources(List<PackResources>)` 是完整 reload 的 pack 注入入口＝6 代 `wrapResourceManager` 等价；只重载配方**不用**它。）
  - **无需重建干净 RM（与 6 代不同）**：7 代虚拟数据包在 `createPackResources`（完整 reload 的 pack 收集）已注入当前 `server.getResourceManager()`；`reload()` 只更新虚拟包内容、不重复插入。故只重载配方**直接用 `server.getResourceManager()`** 扫描即可（`CleanServerResources` 仅 6 代/PC-1 需要）。
  - `RECIPES_AFTER_LOADED` 由 `KubeJSReloadListener`（`ResourceManagerReloadListener`）在完整 reload 触发；只重载配方不走该 listener → 不自动触发，**可选**手动 `ServerEvents.RECIPES_AFTER_LOADED.post(...)`（多数场景无需）。

### 6.3 兼容层版本化骨架

```java
// compat/kubejs/KubeJsRecipeReload.java（示意，两版实现不同）
//? if forge {
/*// KubeJS 6：wrapResourceManager 重跑脚本+设 instance -> invokeApply(mixin cancel) -> postAfterRecipes()
ServerScriptManager.instance.wrapResourceManager(clean);
((RecipeManagerInvoker) rm).reloadonlydata$invokeApply(wrapped, wrapped, InactiveProfiler.INSTANCE);
KubeJSReloadListener.postAfterRecipes();
*///?} else {
// KubeJS 7（R2 已核实）：var sm = ((RecipeManagerKJS) rm).kjs$getResources().kjs$getServerScriptManager(); sm.reload();
// 直接用 server.getResourceManager()（已含虚拟包，无需干净 RM）扫描 -> invokeApply（HEAD/TAIL 自动介入）
// ((RecipeManagerInvoker) rm).reloadonlydata$invokeApply(map, server.getResourceManager(), InactiveProfiler.INSTANCE);
//?}
```

> 通过 `modCompileOnly` 软依赖 KubeJS 才能直接引用这些类；运行期用 `ModList`/`LoadingModList` 判断是否加载并**类隔离**加载兼容类（避免无 KubeJS 时 `NoClassDefFoundError`）。KubeJS 相关的 duck/invoker mixin（若需要）用 Mixin Config Plugin `shouldApplyMixin()` 按 `kubejs` 存在与否条件启用。

---

## 7. 映射到 reloadonlydata / 隔离清单

| 平台边界 | 两版是否需隔离 | 依据 |
| --- | --- | --- |
| mod 主类 / `@Mod` 总线 import | **是**（`//? if`）| §1 |
| 命令注册事件 import | **是**（`//? if`，仅 import）| §1 |
| 命令体 / dispatcher | 否 | §1 |
| Mixin `@Invoker apply` | 否（两版共用）| §2 |
| 扫描 recipes → map | 否 | §2 |
| `getRecipes().size()` 统计 | 否 | §2 |
| 配方同步（packet + recipe book）| 否 | §3 |
| 条件配方 | 否（apply 内部处理）| §4 |
| 干净资源管理器 | 否 | §5 |
| **KubeJS 兼容层** | **是**（整类隔离，API 完全不同）| §6 |

落地包归属：`command/`（命令）、`mixin/`（Invoker + MixinConfigPlugin）、`reload/`（Vanilla 策略、同步、扫描）、`compat/kubejs/`（版本化兼容层）。

---

## 修订记录
- 全文替换为 reloadonlydata 平台边界：命令注册、`RecipeManager`/`RecipeHolder`、`ClientboundUpdateRecipesPacket`、条件配方、`PackRepository`、KubeJS 6 vs 7 兼容差异；移除原 Blackboard 的方块/方块实体/`SyncedBlockEntity`/网络通道内容。KubeJS 差异依据 `2001`/`2101` 真实源码核实；KubeJS 7 脚本重载入口与 AFTER_LOADED 触发标为待落地验证。
- （PA-3/Agent3）R2 已核实并落地 §6.2/§6.3：7 代脚本重载入口＝`ServerScriptManager.reload()`（经 `RecipeManagerKJS.kjs$getResources().kjs$getServerScriptManager()`）；`kjs$resources` 每次 `ReloadableServerResources` 构造时绑定、复用持久有效；**7 代无需重建干净 RM**（虚拟包已在当前 RM）；`RECIPES_AFTER_LOADED` 可选手动 post。
