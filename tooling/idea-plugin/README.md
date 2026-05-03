# kklang IDEA Plugin / kklang IDEA 插件

本模块生成可安装到本地 IntelliJ IDEA 的 `kklang` 插件。
This module builds the `kklang` plugin installable into the local IntelliJ IDEA.

## Icon / 图标

项目主图标位于 `assets/kklang-icon.png`，IDEA 插件从 `src/main/resources/icons/kklang.png` 和 `src/main/resources/icons/kklang@2x.png` 打包小尺寸图标。
The project icon lives at `assets/kklang-icon.png`, and the IDEA plugin packages
small icon variants from `src/main/resources/icons/kklang.png` and
`src/main/resources/icons/kklang@2x.png`.

IDEA 中的 `.kk` 文件类型和 `kklang` 单文件运行配置必须引用同一个插件图标资源。
The `.kk` file type and the `kklang` single-file run configuration in IDEA must
reference the same plugin icon resource.

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

安装后需要重启 IntelliJ IDEA，才能加载新的 `.kk` 插件能力。
Restart IntelliJ IDEA after installation so it can load the updated `.kk` plugin
capabilities.

## Debug / 调试

`.kk` 单文件运行配置在 Run executor 下使用 compiler/core 的内存执行路径；在 Debug executor 下会先构建 `:runtime:kn:linkKkrunDebugExecutableHost`，然后启动 `lldb -- kkrun.kexe <当前 .kk 文件>`。
The `.kk` single-file run configuration uses the in-memory `compiler/core`
execution path under the Run executor; under the Debug executor it first builds
`:runtime:kn:linkKkrunDebugExecutableHost` and then starts
`lldb -- kkrun.kexe <current .kk file>`.

Debug console 会显示 C runtime 断点提示，例如 `breakpoint set --file kklang_runtime.c --name kk_value_int64`，并把输入框中的命令写入 LLDB stdin。
The Debug console shows a C-runtime breakpoint hint, such as
`breakpoint set --file kklang_runtime.c --name kk_value_int64`, and writes
commands from the input field to LLDB stdin.
