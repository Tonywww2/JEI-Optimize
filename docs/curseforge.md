# Just Enough Threads

**Get into your world faster.** Just Enough Threads moves JEI's heaviest startup work off the main thread and spreads it across your CPU cores, so large modpacks stop freezing on the loading screen while JEI builds its search index.

## What it does

**Off-thread ingredient search index**

JEI normally builds its ingredient filter — the search index over every item and fluid — on the main thread while you wait on the loading screen. In a big pack that can take several seconds. Just Enough Threads builds it on worker threads **after** you enter the world, then swaps the finished index into JEI. The result is identical to JEI's own, and if the off-thread build runs into trouble it falls back to JEI's normal build.

_What you will see:_ the JEI item list appears a moment after you spawn, instead of holding up world load.

**Experimental recipe ingredient pre-resolution**

Before JEI can inspect its built-in recipes (crafting, smelting, stonecutting, and more), every ingredient tag has to be resolved into a concrete list of items. Minecraft does that lazily and JEI triggers all of it during startup. Just Enough Threads does the resolution across CPU cores up front, then hands back to JEI. JEI's own validation and registration still run exactly as they normally do, on the main thread, just against data that is already resolved.

This option is **off by default** because custom recipes and lazy ingredient caches are not always safe to access from worker threads. It remains available for controlled testing on a specific pack.

**Generated anvil recipes**

JEI's generated repair and enchanted-book combination recipes are hidden by default. Generating every item and enchantment combination can take minutes in very large packs. Set `disableAnvilRepairRecipes = false` or `disableAnvilEnchantRecipes = false` to restore either class of recipes.

## Performance

Measured in a large modpack (roughly 21,000 items and fluids and 34,000 vanilla-type recipes), entering the same world on the same machine with the optimizations off vs on:

| JEI startup          |Off    |On     |
| -------------------- |------ |------ |
| Building runtime     |5.18 s |0.55 s |
| Starting JEI (total) |10.7 s |6.4 s  |

The biggest single cost — the ingredient index — no longer blocks loading; it builds in the background once you are already in the world. Numbers vary with hardware and pack size.

## Configuration

Config file: `config/jei_optimize-client.toml`. Every optimization can be toggled independently.

*   `enabled` — master switch for the whole mod.
*   `asyncStartup` — run JEI startup serially on a dedicated background thread, with generation-safe client-thread publication and immediate cancellation on world exit.
*   `asyncIngredientFilter` — build the ingredient search index off-thread after world entry.
*   `parallelVanillaRecipes` — experimental recipe ingredient pre-resolution; disabled by default.
*   `disableAnvilRepairRecipes` / `disableAnvilEnchantRecipes` — hide generated anvil recipes; enabled by default.

If you ever run into a problem on world entry, turn off the individual options, or set `enabled = false` to fully restore stock JEI behavior.

## Requirements

| Minecraft |Loader          |JEI                     |
| --------- |--------------- |----------------------- |
| 1.20.1    |Forge 47.4.4+   |15.20.0.120 - 15.48.x   |
| 1.21.1    |NeoForge 21.1.x |19.27.0.340             |

Client-side only. JEI is required. On a JEI version that is not supported yet, the affected optimization switches itself off and JEI keeps its normal behavior.

## Notes

Not affiliated with JEI. 

# Just Enough Threads

**更快进入世界。** Just Enough Threads 把 JEI 启动时最重的工作移出主线程、分散到多个 CPU 核心,让大型整合包在加载界面上不再因为 JEI 构建搜索索引而卡住。

## 功能

**离主线程构建物品搜索索引**

JEI 默认在主线程上构建物品筛选器——也就是覆盖所有物品与流体的搜索索引——而你只能在加载界面干等。在大型整合包里,这一步要好几秒。Just Enough Threads 会在你**进入世界之后**用工作线程构建它,完成后再把成品索引原子替换进 JEI。结果与 JEI 原生完全一致;如果离主线程的构建出现问题,会自动回退到 JEI 的常规构建。

_你会看到:_ JEI 物品列表在你进入世界后稍等片刻才出现,而不是拖慢世界加载。

**保持界面响应的串行 JEI 启动**

`asyncStartup` 会在一条专用后台线程上按 JEI 原顺序执行完整启动流程。插件回调不会并发执行;完成后的 runtime 只会在客户端线程上原子发布。退出世界时,当前 generation 会立即失效,启动线程和派生任务会被取消,旧 runtime 不会发布到下一个世界。

**实验性并行预解析配方材料**

JEI 在检查内置配方(合成、熔炼、切石等)之前,需要先把每一条材料标签解析成具体的物品列表。Minecraft 采用惰性解析,而 JEI 启动时会把这些解析全部触发一遍。Just Enough Threads 提前用多个 CPU 核心完成这项解析,再交回 JEI。JEI 自身的校验与注册照常在主线程上运行,只是读到的数据已经解析完毕。

此选项**默认关闭**,因为自定义配方与惰性材料缓存不一定能安全地从工作线程访问。它仍可用于针对特定整合包的受控测试。

**自动生成的铁砧配方**

JEI 自动生成的材料修复与附魔书组合配方默认隐藏。在超大型整合包中,枚举所有物品与附魔组合可能耗时数分钟。将 `disableAnvilRepairRecipes = false` 或 `disableAnvilEnchantRecipes = false` 即可恢复对应类型的配方。

## 性能

在一个大型整合包(约 21,000 个物品/流体、34,000 条原版类配方)中,于同一台机器进入同一世界,关闭 vs 开启优化:

| JEI 启动阶段         |关闭     |开启     |
| ---------------- |------ |------ |
| Building runtime |5.18 s |0.55 s |
| Starting JEI(总计) |10.7 s |6.4 s  |

最大的单项开销——物品索引——不再阻塞加载;它会在你已经进入世界之后于后台构建。具体数字随硬件与整合包规模而变化。

## 配置

配置文件:`config/jei_optimize-client.toml`。每一项优化都可以单独开关。

*   `enabled` — 整个 mod 的总开关。
*   `asyncStartup` — 在专用后台线程上串行启动 JEI;退出世界时立即取消,默认开启。
*   `asyncIngredientFilter` — 进入世界后离主线程构建物品搜索索引。
*   `parallelVanillaRecipes` — 实验性并行预解析配方材料;默认关闭。
*   `disableAnvilRepairRecipes` / `disableAnvilEnchantRecipes` — 隐藏自动生成的铁砧配方;默认开启。

如果进入世界时遇到任何问题,可以逐项关闭这些选项,或将 `enabled = false` 以完全恢复 JEI 的原生行为。

## 需求

| Minecraft |Loader          |JEI                    |
| --------- |--------------- |---------------------- |
| 1.20.1    |Forge 47.4.4+   |15.20.0.120 - 15.48.x  |
| 1.21.1    |NeoForge 21.1.x |19.27.0.340            |

仅客户端需要安装,并且必须装有 JEI。遇到尚未适配的 JEI 版本时,受影响的那一项优化会自动关闭,JEI 保持原生行为。

## 注意事项

本项目与 JEI 无隶属关系。