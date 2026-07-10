# 参考：多加载器构建（Architectury Loom flat + Stonecutter，纯 Java）

> 适用：**reloadonlydata** 同时构建 **Forge 1.20.1**（Java 17）与 **NeoForge 1.21.1**（Java 21）。
> **纯 Java**，不使用 KotlinLangForge / Kotlin 运行时。
> Stonecutter 见 [stonecutter.md](stonecutter.md)；两版平台 API 差异见 [loader-platform-api.md](loader-platform-api.md)。

目录：
1. 构建工具结论
2. 目录与属性布局
3. settings.gradle.kts
4. stonecutter.gradle.kts
5. 共享 build.gradle.kts（flat，单脚本双加载器）
6. Mixin 接入（Loom 内建）
7. 元数据文件（mods.toml / neoforge.mods.toml）
8. KubeJS 软依赖接入
9. 待确认项与验证命令

---

## 1. 构建工具结论

| 目标 | `loom.platform` | 加载器依赖（Loom 配置） | Java |
| --- | --- | --- | --- |
| Forge 1.20.1 | `forge` | `net.minecraftforge:forge:1.20.1-47.4.4`（`forge` 配置）| 17 |
| NeoForge 1.21.1 | `neoforge` | `net.neoforged:neoforge:21.1.x`（`neoForge` 配置）| 21 |

- 构建：**Architectury Loom**（`dev.architectury.loom`）。**flat 模式**：单一 `build.gradle.kts`，按节点属性 `loom.platform` 切 Forge/NeoForge。
- 映射：`loom.officialMojangMappings()`（两版开发都用官方名，Mixin refmap 由 Loom 各自处理）。
- Stonecutter `0.9.6`，`kotlinController = true`（仅构建脚本用 Kotlin DSL；**源码是纯 Java**）。
- **Mixin 由 Loom 内建支持**（Forge/NeoForge 自带 Mixin 运行时），无需额外插件。

---

## 2. 目录与属性布局

```
reloadonlydata/
├─ settings.gradle.kts
├─ stonecutter.gradle.kts               # 控制器（active + parameters）
├─ build.gradle.kts                     # 单一共享脚本（flat，双加载器）
├─ gradle.properties                    # Gradle 选项 + mod.* 元数据
├─ versions/
│  ├─ 1.20.1-forge/gradle.properties
│  └─ 1.21.1-neoforge/gradle.properties
└─ src/main/
   ├─ java/com/example/reloadonlydata/...
   └─ resources/
      ├─ reloadonlydata.mixins.json          # Mixin 配置（两版共用）
      ├─ META-INF/mods.toml                     # Forge
      └─ META-INF/neoforge.mods.toml            # NeoForge
```

`./gradle.properties`（根）：
```properties
org.gradle.jvmargs=-Xmx2G

mod.id=reloadonlydata
mod.name=reloadonlydata
mod.version=0.1.0
mod.group=com.tonywww.reloadonlydata
```

`./versions/1.20.1-forge/gradle.properties`：
```properties
vers.mcVersion=1.20.1
vers.deps.fml=47.4.4
loom.platform=forge
deps.kubejs=2001.6.x-build.xxx          # KubeJS 6（Forge），用构建时最新
```

`./versions/1.21.1-neoforge/gradle.properties`：
```properties
vers.mcVersion=1.21.1
vers.deps.fml=21.1.193                   # 用实际最新的 1.21.1 NeoForge 版本
loom.platform=neoforge
deps.kubejs=2101.7.x-build.xxx          # KubeJS 7（NeoForge），用构建时最新
```

> `loom.platform=forge`/`neoforge` 写在**节点 gradle.properties**，是 flat Loom 切换加载器的关键。

---

## 3. settings.gradle.kts

见 [stonecutter.md](stonecutter.md) §3（`rootProject.name = "reloadonlydata"`、插件仓库、`stonecutter { kotlinController = true; create(rootProject){ version(...) } }`）。

---

## 4. stonecutter.gradle.kts

```kotlin
plugins { id("dev.kikugie.stonecutter") }

stonecutter active "1.21.1-neoforge"

stonecutter parameters {
    val loader = current.project.substringAfterLast('-')   // forge / neoforge
    constants { match(loader, "forge", "neoforge") }        // 启用 //? if forge / //? if neoforge
}
```

---

## 5. 共享 build.gradle.kts（flat，单脚本双加载器）

```kotlin
import net.fabricmc.loom.util.ModPlatform

plugins {
    id("dev.architectury.loom") version "1.11-SNAPSHOT"
}

val loader = loom.platform.get()
val mcVersion = property("vers.mcVersion").toString()

group = property("mod.group").toString()
version = "${property("mod.version")}+$mcVersion"
base.archivesName = "${property("mod.id")}-${loader.id()}"

val javaVersion = if (stonecutter.eval(mcVersion, ">=1.20.6")) 21 else 17

loom {
    silentMojangMappingsLicense()
    // Mixin 配置（两版共用同一 json；Loom 生成 refmap）
    mixin { defaultRefmapName = "${property("mod.id")}.refmap.json" }
    if (stonecutter.current.isActive) {
        runConfigs.all { ideConfigGenerated(true); runDir("../../run") }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.minecraftforge.net/")
    maven("https://maven.saps.dev/releases")        // KubeJS（软依赖，见 §8）
    maven("https://maven.latvian.dev/releases")     // KubeJS 备用镜像
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(loom.officialMojangMappings())

    if (loader == ModPlatform.FORGE)
        "forge"("net.minecraftforge:forge:$mcVersion-${property("vers.deps.fml")}")
    else
        "neoForge"("net.neoforged:neoforge:${property("vers.deps.fml")}")

    // KubeJS 软依赖：仅编译期，运行期由玩家安装（见 §8）
    // modCompileOnly("dev.latvian.mods:kubejs-${if (loader == ModPlatform.NEOFORGE) "neoforge" else "forge"}:${property("deps.kubejs")}") {
    //     exclude(group = "com.github.rtyley", module = "animated-gif-lib-for-java")
    // }
}

tasks {
    // 关键：用预处理后的源码构建
    named("createMinecraftArtifacts") { dependsOn("stonecutterGenerate") }

    processResources {
        val props = mapOf(
            "id" to property("mod.id"),
            "name" to property("mod.name"),
            "version" to property("mod.version"),
        )
        inputs.properties(props)
        filesMatching(listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml")) { expand(props) }
        // 仅保留当前加载器的元数据文件
        exclude(if (loader == ModPlatform.NEOFORGE) "META-INF/mods.toml" else "META-INF/neoforge.mods.toml")
    }
    withType<JavaCompile> { options.release = javaVersion }
}

java {
    withSourcesJar()
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
}
```

> 纯 Java 无 Kotlin 依赖，也无 KLF——故**不存在** KotlinLangForge 那套 `kotlin-stdlib` 重映射 / langprovider 模块层的 dev 运行崩溃。构建链干净。

---

## 6. Mixin 接入（Loom 内建）

本项目唯一必需的 Mixin 是 `RecipeManagerInvoker`（`@Invoker` 暴露 `RecipeManager.apply`），外加可选的 `reloadonlydataMixinPlugin`（带判断的 Mixin Config Plugin）。

`src/main/resources/reloadonlydata.mixins.json`：
```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.tonywww.reloadonlydata.mixin",
  "compatibilityLevel": "JAVA_17",
  "refmap": "reloadonlydata.refmap.json",
  "mixins": [
    "RecipeManagerInvoker"
  ],
  "plugin": "com.tonywww.reloadonlydata.mixin.reloadonlydataMixinPlugin"
}
```

要点：
- **Invoker 两版共用**：`@Invoker("apply")`，方法签名两版一致，Loom 生成的 refmap 在 Forge 运行时映射到 SRG、在 NeoForge 运行时用 Mojmap。无需 Stonecutter 隔离。
- `compatibilityLevel`：1.20.1(Java17) 与 1.21.1(Java21) 用 `JAVA_17` 即可兼容（也可两版分别设 `JAVA_21`，用 `//? if` 隔离；`JAVA_17` 通用更省事）。
- Mixin config **必须在元数据里声明**（见 §7），否则不加载。

---

## 7. 元数据文件

> `modLoader = "javafml"`（**纯 Java**；不是 `klf`）。`${...}` 由 `processResources.expand` 填充。

**Forge** `src/main/resources/META-INF/mods.toml`：
```toml
modLoader = "javafml"
loaderVersion = "[47,)"
license = "All Rights Reserved"

[[mixins]]
config = "reloadonlydata.mixins.json"

[[mods]]
modId = "${id}"
version = "${version}"
displayName = "${name}"

[[dependencies.${id}]]
modId = "forge"
mandatory = true
versionRange = "[47,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.${id}]]
modId = "minecraft"
mandatory = true
versionRange = "[1.20.1,1.21)"
ordering = "NONE"
side = "BOTH"

# KubeJS 为软依赖：可选、加载顺序在本 mod 之前（若安装）
[[dependencies.${id}]]
modId = "kubejs"
mandatory = false
ordering = "AFTER"
side = "BOTH"
```

**NeoForge** `src/main/resources/META-INF/neoforge.mods.toml`：
```toml
modLoader = "javafml"
loaderVersion = "[4,)"          # NeoForge FML loader 版本，按实际确认
license = "All Rights Reserved"

[[mixins]]
config = "reloadonlydata.mixins.json"

[[mods]]
modId = "${id}"
version = "${version}"
displayName = "${name}"

[[dependencies.${id}]]
modId = "neoforge"
type = "required"
versionRange = "[21.1,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.${id}]]
modId = "minecraft"
type = "required"
versionRange = "[1.21.1,1.21.2)"
ordering = "NONE"
side = "BOTH"

[[dependencies.${id}]]
modId = "kubejs"
type = "optional"
ordering = "AFTER"
side = "BOTH"
```

> Forge 用 `mandatory=true/false`；NeoForge 用 `type="required"/"optional"`。版本范围按实际依赖确认。

---

## 8. KubeJS 软依赖接入

- 仓库：`maven("https://maven.saps.dev/releases")`（KubeJS 7 常用）/ `maven("https://maven.latvian.dev/releases")`。
- 工件：`dev.latvian.mods:kubejs-forge:<ver>`（1.20.1 = KubeJS 6，分支 `2001`）/ `dev.latvian.mods:kubejs-neoforge:<ver>`（1.21.1 = KubeJS 7，分支 `2101`）。
- 软依赖写法（Loom）：`modCompileOnly(...)`；本地联调可加 `modLocalRuntime(...)`。
- ⚠️ **排除 JitPack 传递依赖**：KubeJS 传递依赖 `com.github.rtyley:animated-gif-lib-for-java`（仅在 JitPack）。因 KubeJS 是 `modCompileOnly`（不打包、运行期不需要）且本 mod 不碰该库，`exclude` 之，避免解析失败拖垮整个配置阶段：
  ```kotlin
  modCompileOnly("dev.latvian.mods:kubejs-${if (loader == ModPlatform.NEOFORGE) "neoforge" else "forge"}:${property("deps.kubejs")}") {
      exclude(group = "com.github.rtyley", module = "animated-gif-lib-for-java")
  }
  ```
- **两版 KubeJS API 完全不同**（KubeJS 6 vs 7），兼容代码需版本化——见 [loader-platform-api.md](loader-platform-api.md) §6。

---

## 9. 待确认项与验证命令

**待确认（落地时回写实际版本号）**：
1. `net.neoforged:neoforge:21.1.x`（用最新 1.21.1）、NeoForge `loaderVersion`。
2. `deps.kubejs`（KubeJS 6 / 7 各自最新构建号）。
3. Architectury Loom 版本（`1.11-SNAPSHOT` 或届时稳定版）。
4. `ModPlatform#id()` 取字符串的正确方法（或直接用 `property("loom.platform")`）。

**验证命令**：
```powershell
.\gradlew.bat :1.20.1-forge:build
.\gradlew.bat :1.21.1-neoforge:build
.\gradlew.bat :1.20.1-forge:runClient --console=plain      # 关窗口即结束
.\gradlew.bat :1.21.1-neoforge:runClient --console=plain
```
> `runClient` 即使游戏内崩溃也可能 `BUILD SUCCESSFUL`（崩溃时进程退出）。判定成败要看 `run/` 日志有无 `Exception`、是否进入标题页/世界。
> 若 `fabric-loom` 缓存锁残留（`Lock for cache ... held by pid ...`），先 `.\gradlew.bat --stop` 再重跑。
> 实测 `/reloadrecipes`：分别在无 KubeJS / 有 KubeJS 两种环境下验证（KubeJS 6 装 Forge 侧、KubeJS 7 装 NeoForge 侧）。

---

## 修订记录
- 改写为 reloadonlydata（纯 Java）版：Architectury Loom flat + Stonecutter 双版本骨架，加 Mixin（Loom 内建）与 KubeJS 软依赖；移除 KotlinLangForge / Kotlin 运行时及其 dev 运行崩溃章节（纯 Java 不适用）；`modLoader` 改为 `javafml`。
