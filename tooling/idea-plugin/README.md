# kklang IDEA Plugin / kklang IDEA 插件

本模块生成可安装到本地 IntelliJ IDEA 的 `kklang` 插件。
This module builds the `kklang` plugin installable into the local IntelliJ IDEA.

## Build / 构建

默认使用 `/Applications/IntelliJ IDEA.app` 作为编译时 IDEA SDK。
The default compile-time IDEA SDK is `/Applications/IntelliJ IDEA.app`.

如需指定其他 IDEA 安装路径，设置 `KKLANG_IDEA_HOME`。
Set `KKLANG_IDEA_HOME` to use another IDEA installation path.

```bash
JAVA_HOME=/Users/llonvne/Library/Java/JavaVirtualMachines/openjdk-25.0.2/Contents/Home ./gradlew :tooling:idea-plugin:buildPluginZip
```

## Install / 安装

构建产物位于 `tooling/idea-plugin/build/distributions/kklang-idea-plugin-1.0-SNAPSHOT.zip`。
The build artifact is
`tooling/idea-plugin/build/distributions/kklang-idea-plugin-1.0-SNAPSHOT.zip`.

在 IDEA 中通过 `Settings | Plugins | Install Plugin from Disk...` 选择该 zip。
In IDEA, choose the zip through `Settings | Plugins | Install Plugin from
Disk...`.

## Local Auto Install / 本地自动安装

本模块提供 `installLocalIdeaPlugin`，会构建插件 zip、移除旧的 `kklang-idea-plugin` 目录，并把新插件安装到本机 IntelliJ IDEA 2026.1 的 plugins 目录。
This module provides `installLocalIdeaPlugin`; it builds the plugin zip, removes
the old `kklang-idea-plugin` directory, and installs the new plugin into the
local IntelliJ IDEA 2026.1 plugins directory.

```bash
JAVA_HOME=/Users/llonvne/Library/Java/JavaVirtualMachines/openjdk-25.0.2/Contents/Home ./gradlew :tooling:idea-plugin:installLocalIdeaPlugin
```

默认安装目录是 `~/Library/Application Support/JetBrains/IntelliJIdea2026.1/plugins/kklang-idea-plugin`。
The default install directory is
`~/Library/Application Support/JetBrains/IntelliJIdea2026.1/plugins/kklang-idea-plugin`.

如需安装到其他 IDEA 配置目录，设置 `KKLANG_IDEA_CONFIG_DIR`。
Set `KKLANG_IDEA_CONFIG_DIR` to install into another IDEA configuration
directory.

安装后需要重启 IntelliJ IDEA，才能加载新的 `.kk` 高亮插件。
Restart IntelliJ IDEA after installation so it can load the updated `.kk`
highlighting plugin.
