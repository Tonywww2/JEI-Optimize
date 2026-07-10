# 参考：Stonecutter 多版本/多加载器（reloadonlydata 版）

> 来源：Stonecutter 官方文档 https://stonecutter.kikugie.dev/wiki/ 与官方/社区模板（已核实）。
> 适用：**reloadonlydata** 同时维护 **Forge 1.20.1**（Java 17）与 **NeoForge 1.21.1**（Java 21）。
> 本项目为**纯 Java 工具 mod**：核心只有「一条 `/reloadrecipes` 命令 + Mixin 访问 `RecipeManager.apply` + KubeJS 软兼容」，无方块/GUI。
> 构建脚本（Architectury Loom flat + Mixin + KubeJS 软依赖）见 [multiloader-build.md](multiloader-build.md)；两版平台 API 差异见 [loader-platform-api.md](loader-platform-api.md)。

目录：
1. 要求与插件
2. 项目模型（Tree / Branch / Node）
3. settings.gradle.kts
4. stonecutter.gradle.kts（控制器）
5. build 脚本读取的 Stonecutter 值
6. 版本化注释语法
7. 常量 / swap / 替换
8. 本项目落地基线（隔离点清单）
9. 关键陷阱

---

## 1. 要求与插件

- **Gradle ≥ 9.0**。
- Stonecutter 插件：`dev.kikugie.stonecutter`，当前 `0.9.6`。
- 插件仓库（`settings.gradle.kts` 的 `pluginManagement.repositories`）：
  - `gradlePluginPortal()`、`mavenCentral()`
  - `maven("https://maven.kikugie.dev/releases")`、`maven("https://maven.kikugie.dev/snapshots")`
  - 加载器仓库：`maven("https://maven.neoforged.net/releases/")`、`maven("https://maven.minecraftforge.net/")`、`maven("https://maven.architectury.dev")`、`maven("https://maven.fabricmc.net/")`

---

## 2. 项目模型（三层）

- **Tree（树）**：根项目，持有 `stonecutter.gradle.kts`，是所有子项目的同步点。`stonecutter.create(rootProject) {}`。
- **Branch（分支）**：持有共享 `src/` 与 `versions/...`。本项目单分支（根分支，空串 `""`）。
- **Node（节点）**：每个受支持的版本变体，对应 `versions/<project>/`，产出最终 jar。节点三要素：
  - **project**：目录名 / 唯一标识，如 `1.20.1-forge`、`1.21.1-neoforge`。
  - **version**：用于注释预处理的逻辑版本，如 `1.20.1`、`1.21.1`。**必须与 project 分离**（见 §9-1）。
  - **build script**：本项目用 **flat 单脚本**，故**不**为节点单独指定 buildscript。

> **本项目采用「Flat + Architectury Loom」**（Stonecutter 官方「Flat → Architectury」方案）：单一 `src/`、单一共享 `build.gradle.kts`，按节点属性 `loom.platform` 切 Forge/NeoForge。与 split-buildscript（ModDevGradle）不同。

---

## 3. settings.gradle.kts

```kotlin
rootProject.name = "reloadonlydata"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.architectury.dev")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
}

plugins { id("dev.kikugie.stonecutter") version "0.9.6" }

stonecutter {
    kotlinController = true                 // 控制器用 Kotlin DSL（仅构建脚本，源码仍是 Java）
    create(rootProject) {
        version("1.20.1-forge", "1.20.1")
        version("1.21.1-neoforge", "1.21.1")
        vcsVersion = "1.21.1-neoforge"      // 提交前 Reset 回到的版本
    }
}
```

要点（已核实）：
- `version("项目名", "逻辑版本")`：**必须分离**，否则 `1.20.1-forge` 会被当成 SemVer 预发布，破坏 `//? if >=1.20.1` 判断。
- flat Loom 用**单一共享 `build.gradle.kts`**，故节点**不**调 `.buildscript(...)`。
- `kotlinController = true` 只影响 `stonecutter.gradle.kts` / `build.gradle.kts` 用 Kotlin DSL 写；**mod 源码是纯 Java**（Stonecutter 只做文本预处理，与源码语言无关）。

---

## 4. stonecutter.gradle.kts（控制器，单例运行）

```kotlin
plugins { id("dev.kikugie.stonecutter") }

stonecutter active "1.21.1-neoforge"        // 当前编辑/链接的活动版本，必须恰好赋值一次

stonecutter parameters {
    val loader = current.project.substringAfterLast('-')   // forge / neoforge
    // 加载器常量：使注释 //? if forge { / //? if neoforge { 可用
    constants { match(loader, "forge", "neoforge") }
    // 可选：跨文件改名替换（见 §7）
}
```

要点：
- `parameters {}`（懒求值）只配置**文件处理器参数**（constants / swaps / replacements）；**不要**在其中配依赖或任务。
- `active` 三形式：字面量、`file("active.txt")`、`null`（detached，CI 构建全部版本）。
- **NeoForge/Forge 关键**：build 脚本里需 `tasks.named("createMinecraftArtifacts") { dependsOn("stonecutterGenerate") }`（见 multiloader-build.md），否则用的是未预处理源码。

---

## 5. build 脚本读取的 Stonecutter 值

`stonecutter` 扩展。常用：
- `stonecutter.current.project`：节点目录名（如 `1.20.1-forge`）。`.substringAfterLast('-')` 取 loader。
- `stonecutter.current.version`：逻辑版本（如 `1.20.1`）。
- `stonecutter.current.isActive`：是否为活动节点（用于只给活动节点生成 IDE run 配置）。
- `stonecutter.eval("1.20.1", ">=1.20.6")`：字符串版本比较，用于选 Java 版本等。

Java 版本映射（本项目）：
```kotlin
val javaVersion = if (stonecutter.eval(mcVersion, ">=1.20.6")) 21 else 17
// 1.21.1 -> 21，1.20.1 -> 17
```

---

## 6. 版本化注释语法（已核实）

Java 注释风格用 `//` 与 `/* */`；JSON5 同；`mods.toml`/`.cfg`/mixin refmap 等用 `#` 或对应风格。

**作用域**：
- 闭合：
  ```java
  //? if >=1.21 {
  /*onlyOn121AndAbove();
  *///?}
  ```
- 行：`//? if <1.21` 仅作用于下一非空行。
- Lookup：`/*? cond >>*/param`。

**分支**（链中只有最后一支可非闭合）：
```java
//? if forge {
import net.minecraftforge.event.RegisterCommandsEvent;
//?} else {
/*import net.neoforged.neoforge.event.RegisterCommandsEvent;
*///?}
```

**谓词**：`=`(默认) `!=` `<` `>` `<=` `>=`；`~`（major.minor，如 `~1.20`）；`^`（major）。可链：`//? if ~1.20 <1.20.4`。

**逻辑**：`!`、`||`、`&&`、`()`，如 `//? if forge && >=1.20 {`。

**常量**（多加载器核心）：`//? if forge {` / `//? if neoforge {`（由 §4 的 `constants.match` 提供）。

---

## 7. 常量 / swap / 替换

- **常量**：`constants.match(loader, "forge", "neoforge")`（匹配项为 true）。注释用 `//? if forge {`。
- **swap**（替换整段代码，同一片段在多处重复时用）：
  ```kotlin
  swaps["sync_packet"] = when { current.parsed >= "1.21" -> "..." else -> "..." }
  ```
  注释 `//$ sync_packet` 置于目标行上方。
- **替换**（跨文件查找替换，版本切换时生效）：
  ```kotlin
  // 例：1.21 起 RecipeManager 用 RecipeHolder（若某处需批量改名再用）
  replacements.string(current.parsed >= "1.20.5") { replace("Recipe<?>", "RecipeHolder<?>") }
  ```

> 经验：能用 `//? if` 表达的差异优先用注释；跨文件统一改名才用 string 替换（regex 更慢，慎用）。本项目差异集中在少数几处（命令 import、RecipeManager/RecipeHolder、ClientboundUpdateRecipesPacket、KubeJS 兼容层），**优先就地 `//? if` 注释**。

---

## 8. 本项目落地基线（隔离点清单）

节点：`1.20.1-forge`（Java 17）、`1.21.1-neoforge`（Java 21）。单一 `src/main/java`。

**需要 Stonecutter `//? if forge {` / `//? if neoforge {` 隔离的点**（详见 [loader-platform-api.md](loader-platform-api.md)）：

| 隔离点 | Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|---|
| mod 主类 / `@Mod` 事件总线 | `net.minecraftforge.*` | `net.neoforged.*` |
| 命令注册事件 import | `net.minecraftforge.event.RegisterCommandsEvent` | `net.neoforged.neoforge.event.RegisterCommandsEvent` |
| 配方类型 | `Recipe<?>`、`Collection<Recipe<?>>` | `RecipeHolder<?>`、`List<RecipeHolder<?>>` |
| 配方同步包构造 | `new ClientboundUpdateRecipesPacket(recipeManager.getRecipes())` | 同名但入参为 `RecipeHolder` 集合（见平台文档） |
| KubeJS 兼容层 | KubeJS 6：`RecipesEventJS.instance` + `wrapResourceManager` + `postAfterRecipes()` | KubeJS 7：`RecipeManagerKJS`/`RecipesKubeEvent`/`kjs$resources`（**不同 API**，整类隔离） |

- **Mixin `@Invoker` 访问 `RecipeManager.apply`**：两版方法名同为 `apply`、签名同为 `(Map<ResourceLocation, JsonElement>, ResourceManager, ProfilerFiller)`，故 **Invoker 接口可两版共用**，通常无需注释隔离（Loom refmap 各自映射）。
- 命令逻辑、扫描 recipes 目录、`PackRepository.openAllSelected()` 重建资源管理器——**两版一致**，无需隔离。
- KubeJS 兼容层差异最大，建议放独立类（如 `compat/kubejs/`），整段用 `//? if forge {`/`//? if neoforge {` 或两个平级实现类。

settings 与控制器见 §3/§4；构建脚本见 [multiloader-build.md](multiloader-build.md)。

---

## 9. 关键陷阱（已核实）

1. **节点名/版本必须分离**（`version("1.20.1-forge","1.20.1")`），否则版本谓词错乱。
2. **NeoForge/Forge 必须** `createMinecraftArtifacts dependsOn stonecutterGenerate`，否则用未预处理源码。
3. flat Loom 用单一 `build.gradle.kts`，节点**不**指定 buildscript。
4. `parameters {}` 只放 Stonecutter 配置，不放依赖/任务。
5. 闭合分支链中只有最后一支能省略 `{}`。
6. 提交前运行 `Reset active version`，避免预处理噪声进入 git。
7. 本项目源码是 **Java**；`kotlinController = true` 仅指构建脚本用 Kotlin DSL，不引入 Kotlin 运行时、也不需要 KotlinLangForge。

---

## 修订记录
- 依据 Stonecutter 官方 wiki 与官方多加载器模板，改写为 reloadonlydata（纯 Java、Forge 1.20.1 + NeoForge 1.21.1、Loom flat）版；隔离点清单对齐本项目的命令/RecipeManager/KubeJS 场景。
