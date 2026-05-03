package cn.llonvne.kklang.idea

import cn.llonvne.kklang.execution.ExecutionEngine
import cn.llonvne.kklang.execution.ExecutionResult
import cn.llonvne.kklang.execution.ExecutionValue
import cn.llonvne.kklang.frontend.SourceText
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.icons.AllIcons
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jdom.Element
import java.awt.BorderLayout
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readText

/**
 * IDEA 单文件运行产生的 diagnostic 快照。
 * Diagnostic snapshot produced by IDEA single-file execution.
 */
data class KkIdeaRunDiagnostic(
    val code: String,
    val message: String,
)

/**
 * IDEA 单文件运行结果，保留 stdout、退出码和可显示文本。
 * IDEA single-file run result preserving stdout, exit code, and display text.
 */
sealed interface KkIdeaRunResult {
    val stdout: String
    val exitCode: Int

    /**
     * 返回 Run console 中显示的完整文本。
     * Returns the complete text shown in the Run console.
     */
    fun consoleText(): String

    /**
     * 编译执行成功，包含最终表达式值。
     * Successful compile/run result containing the final expression value.
     */
    data class Success(
        override val stdout: String,
        val valueText: String,
    ) : KkIdeaRunResult {
        override val exitCode: Int = 0

        /**
         * 渲染成功运行的 stdout、结果值和退出码。
         * Renders stdout, result value, and exit code for a successful run.
         */
        override fun consoleText(): String =
            buildConsoleText(stdout) {
                append("Result: ").append(valueText).append('\n')
                append("Process finished with exit code 0\n")
            }
    }

    /**
     * 编译或执行失败，包含 diagnostics。
     * Failed compile/run result containing diagnostics.
     */
    data class Failure(
        override val stdout: String,
        val diagnostics: List<KkIdeaRunDiagnostic>,
    ) : KkIdeaRunResult {
        override val exitCode: Int = 1

        /**
         * 渲染失败运行的 stdout、diagnostics 和退出码。
         * Renders stdout, diagnostics, and exit code for a failed run.
         */
        override fun consoleText(): String =
            buildConsoleText(stdout) {
                for (diagnostic in diagnostics) {
                    append(diagnostic.code).append(": ").append(diagnostic.message).append('\n')
                }
                append("Process finished with exit code 1\n")
            }
    }
}

/**
 * 组装 Run console 文本，并在 stdout 后补必要换行。
 * Assembles Run console text and adds a separating newline after stdout when needed.
 */
private fun buildConsoleText(stdout: String, appendTail: StringBuilder.() -> Unit): String =
    buildString {
        append(stdout)
        if (isNotEmpty()) {
            if (!endsWith('\n')) {
                append('\n')
            }
        }
        appendTail()
    }

/**
 * IDEA 单文件运行服务，复用 compiler/core 的 ExecutionEngine。
 * IDEA single-file run service that reuses the compiler/core ExecutionEngine.
 */
class KkIdeaRunService(
    private val executionEngine: ExecutionEngine = ExecutionEngine(),
) {
    /**
     * 编译并执行一份 `.kk` 源码文本。
     * Compiles and executes one `.kk` source text.
     */
    fun execute(sourceName: String, text: String): KkIdeaRunResult {
        require(sourceName.isNotBlank()) { "run source name must not be blank" }
        return when (val result = executionEngine.execute(SourceText.of(sourceName, text))) {
            is ExecutionResult.Success -> KkIdeaRunResult.Success(
                stdout = result.output,
                valueText = result.value.ideaText(),
            )
            is ExecutionResult.Failure -> KkIdeaRunResult.Failure(
                stdout = result.output,
                diagnostics = result.diagnostics.map { KkIdeaRunDiagnostic(code = it.code, message = it.message) },
            )
        }
    }

    /**
     * 返回 IDEA Run console 中的值显示文本。
     * Returns the value display text used by the IDEA Run console.
     */
    private fun ExecutionValue.ideaText(): String =
        if (this is ExecutionValue.Int64) {
            value.toString()
        } else if (this is ExecutionValue.String) {
            "\"$value\""
        } else {
            "Unit"
        }
}

/**
 * kklang 单文件运行配置类型。
 * Run configuration type for a single kklang file.
 */
object KkRunConfigurationType : ConfigurationTypeBase(
    "kklang.single.file",
    "kklang",
    "Run a single .kk file",
    KkIcons.Language,
) {
    val configurationFactory: KkRunConfigurationFactory = KkRunConfigurationFactory(this)

    init {
        addFactory(configurationFactory)
    }
}

/**
 * kklang 单文件运行配置工厂。
 * Factory for kklang single-file run configurations.
 */
class KkRunConfigurationFactory(type: KkRunConfigurationType) : ConfigurationFactory(type) {
    /**
     * 创建项目中的模板运行配置。
     * Creates the template run configuration for a project.
     */
    override fun createTemplateConfiguration(project: Project): KkRunConfiguration =
        KkRunConfiguration(project, this, "kklang file")

    /**
     * 返回稳定的配置工厂 id。
     * Returns the stable configuration factory id.
     */
    override fun getId(): String = "kklang.single.file.factory"
}

/**
 * 从当前 `.kk` PSI 文件自动生成单文件运行配置。
 * Automatically creates a single-file run configuration from the current `.kk` PSI file.
 */
class KkRunConfigurationProducer : LazyRunConfigurationProducer<KkRunConfiguration>(), DumbAware {
    /**
     * 返回单文件运行配置工厂。
     * Returns the single-file run configuration factory.
     */
    override fun getConfigurationFactory(): ConfigurationFactory =
        KkRunConfigurationType.configurationFactory

    /**
     * 从 IDEA context 中的 `.kk` 文件填充运行配置。
     * Populates a run configuration from the `.kk` file in the IDEA context.
     */
    override fun setupConfigurationFromContext(
        configuration: KkRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val file = context.kkPsiFile() ?: return false
        val path = file.virtualFile.path

        configuration.filePath = path
        configuration.name = "Run ${Path.of(path).fileName}"
        sourceElement.set(file)
        return true
    }

    /**
     * 判断已有配置是否已经指向当前 `.kk` 文件。
     * Checks whether an existing configuration already points at the current `.kk` file.
     */
    override fun isConfigurationFromContext(configuration: KkRunConfiguration, context: ConfigurationContext): Boolean {
        val file = context.kkPsiFile() ?: return false
        val contextPath = file.virtualFile.path
        return configuration.filePath.normalizedPath() == contextPath.normalizedPath()
    }
}

/**
 * 从运行 context 提取受支持的 `.kk` PSI 文件。
 * Extracts the supported `.kk` PSI file from a run context.
 */
private fun ConfigurationContext.kkPsiFile(): PsiFile? {
    val location = psiLocation ?: return null
    val file = location.containingFile ?: return null
    if (!file.isSupportedKkFile()) {
        return null
    }
    return file
}

/**
 * 判断 PSI 文件是否是当前插件支持的 `.kk` 文件。
 * Checks whether a PSI file is a `.kk` file supported by the current plugin.
 */
private fun PsiFile.isSupportedKkFile(): Boolean {
    if (fileType != KkFileType) {
        return false
    }
    val virtualFile = virtualFile ?: return false
    return virtualFile.path.endsWith(".kk")
}

/**
 * 规范化本地文件路径以便比较运行配置来源。
 * Normalizes a local file path for comparing run-configuration origins.
 */
private fun String.normalizedPath(): String =
    Path.of(this).normalize().toString()

/**
 * IDEA 标准 run line marker，为当前 `.kk` 文件暴露 current-file 运行入口。
 * IDEA standard run line marker exposing the current-file run entry for `.kk` files.
 */
class KkRunLineMarkerContributor : RunLineMarkerContributor(), DumbAware {
    /**
     * 为 `.kk` 文件的首个 PSI anchor 返回 IDEA 标准 executor actions。
     * Returns IDEA standard executor actions for the first PSI anchor of a `.kk` file.
     */
    override fun getInfo(element: PsiElement): Info? {
        if (!element.isKkRunMarkerAnchor()) {
            return null
        }
        return Info(
            AllIcons.RunConfigurations.TestState.Run,
            ExecutorAction.getActions(0),
        ) { anchor ->
            "Run ${Path.of(anchor.containingFile.virtualFile.path).fileName}"
        }
    }

    /**
     * 告诉 IDEA 当前 contributor 已覆盖 `.kk` 文件可生成的单文件配置。
     * Tells IDEA that this contributor covers the single-file configurations available for `.kk` files.
     */
    override fun producesAllPossibleConfigurations(file: PsiFile): Boolean =
        file.isSupportedKkFile()

    /**
     * 判断一个 PSI element 是否应该承载 `.kk` 文件的 run marker。
     * Checks whether a PSI element should carry the `.kk` file run marker.
     */
    private fun PsiElement.isKkRunMarkerAnchor(): Boolean {
        val file = containingFile ?: return false
        if (!file.isSupportedKkFile()) {
            return false
        }
        return this is PsiFile || textOffset == 0
    }
}

/**
 * IDEA Run console 中暴露的 Native debug 命令。
 * Native debug command exposed in the IDEA Run console.
 */
sealed interface KkNativeDebugCommand {
    /**
     * 返回可追加到 Run console 的文本。
     * Returns text appendable to the Run console.
     */
    fun consoleText(): String

    /**
     * 可用的 Native debug 命令集合。
     * Available Native debug command set.
     */
    data class Available(
        val sourceFilePath: String,
        val repositoryRootPath: String,
        val executablePath: String,
        val buildCommand: String,
        val printCommand: String,
        val lldbCommand: String,
        val debugLaunchCommand: List<String>,
        val debugLaunchCommandText: String,
        val cBreakpointHint: String,
    ) : KkNativeDebugCommand {
        /**
         * 渲染 Native debug executable 和命令提示。
         * Renders the Native debug executable and command hints.
         */
        override fun consoleText(): String =
            buildString {
                append('\n')
                append("Native debug executable: ").append(executablePath).append('\n')
                append("Native debug build: ").append(buildCommand).append('\n')
                append("Native debug task: ").append(printCommand).append('\n')
                append("Native debug LLDB: ").append(lldbCommand).append('\n')
            }

        /**
         * 渲染 Debug executor 启动 LLDB 会话时显示的控制台头部。
         * Renders the console header shown when the Debug executor starts the LLDB session.
         */
        fun debugConsoleText(): String =
            buildString {
                append("Native debug session: LLDB\n")
                append("Native debug executable: ").append(executablePath).append('\n')
                append("Native debug build: ").append(buildCommand).append('\n')
                append("Native debug launch: ").append(debugLaunchCommandText).append('\n')
                append("C runtime breakpoint hint: ").append(cBreakpointHint).append('\n')
                append("Type LLDB commands below.\n")
            }
    }

    /**
     * 当前文件无法映射到 kklang 仓库时的空 debug 命令。
     * Empty debug command used when the current file cannot be mapped to a kklang repository.
     */
    data class Unavailable(val reason: String) : KkNativeDebugCommand {
        /**
         * 不在 Run console 中显示不可用原因，避免普通临时文件运行产生噪声。
         * Does not show the unavailable reason in the Run console to avoid noise for temporary files.
         */
        override fun consoleText(): String = ""
    }
}

/**
 * 根据 `.kk` 文件路径生成 Native debug executable 和 LLDB 命令。
 * Generates the Native debug executable and LLDB commands from a `.kk` file path.
 */
class KkNativeDebugCommandService {
    /**
     * 为指定源码文件生成 Native debug 命令；找不到仓库根目录时返回 unavailable。
     * Generates Native debug commands for a source file and returns unavailable when no repository root is found.
     */
    fun commandFor(sourceFilePath: String): KkNativeDebugCommand {
        require(sourceFilePath.isNotBlank()) { "source file path must not be blank" }
        val sourcePath = Path.of(sourceFilePath).toAbsolutePath().normalize()
        val root = findRepositoryRoot(sourcePath.parent)
            ?: return KkNativeDebugCommand.Unavailable("Cannot find kklang repository root for $sourceFilePath")
        val executable = root.resolve("runtime/kn/build/bin/host/kkrunDebugExecutable/kkrun.kexe")
        val gradlew = root.resolve("gradlew")
        val buildCommand = "${gradlew.shellText()} :runtime:kn:linkKkrunDebugExecutableHost"
        val printCommand =
            "${gradlew.shellText()} :runtime:kn:printRuntimeSingleFileDebugCommand -Pkklang.debug.source=${sourcePath.shellText()}"
        val lldbCommand = "lldb -- ${executable.shellText()} ${sourcePath.shellText()}"
        val launchScript = "cd ${root.shellText()} && $buildCommand && exec $lldbCommand"
        val debugLaunchCommand = listOf("/bin/zsh", "-lc", launchScript)
        val cBreakpointHint = "breakpoint set --file kklang_runtime.c --name kk_value_int64"

        return KkNativeDebugCommand.Available(
            sourceFilePath = sourcePath.pathString,
            repositoryRootPath = root.pathString,
            executablePath = executable.pathString,
            buildCommand = buildCommand,
            printCommand = printCommand,
            lldbCommand = lldbCommand,
            debugLaunchCommand = debugLaunchCommand,
            debugLaunchCommandText = debugLaunchCommand.joinToString(" ") { it.shellText() },
            cBreakpointHint = cBreakpointHint,
        )
    }

    /**
     * 自下而上查找 kklang 仓库根目录。
     * Searches upward for the kklang repository root.
     */
    private fun findRepositoryRoot(start: Path?): Path? {
        var current = start
        while (current != null) {
            if (isRepositoryRoot(current)) {
                return current.toAbsolutePath().normalize()
            }
            current = current.parent
        }
        return null
    }

    /**
     * 判断目录是否具备 kklang 仓库根目录标记。
     * Checks whether a directory has kklang repository-root markers.
     */
    private fun isRepositoryRoot(path: Path): Boolean {
        if (!Files.isRegularFile(path.resolve("settings.gradle.kts"))) {
            return false
        }
        return Files.isDirectory(path.resolve("runtime/kn"))
    }

    /**
     * 返回 shell 命令中可直接使用的单引号路径文本。
     * Returns single-quoted path text that can be used directly in shell commands.
     */
    private fun Path.shellText(): String =
        "'${pathString.replace("'", "'\"'\"'")}'"

    /**
     * 返回 shell 命令中可直接使用的单引号字符串。
     * Returns single-quoted string text that can be used directly in shell commands.
     */
    private fun String.shellText(): String =
        "'${replace("'", "'\"'\"'")}'"
}

/**
 * 启动 Native LLDB debug process 的抽象。
 * Abstraction for starting the Native LLDB debug process.
 */
fun interface KkNativeDebugProcessLauncher {
    /**
     * 使用给定 Native debug command 启动 process handler。
     * Starts a process handler from the given Native debug command.
     */
    fun start(command: KkNativeDebugCommand.Available): ProcessHandler
}

/**
 * 使用 IntelliJ process handler 启动 LLDB。
 * Starts LLDB through an IntelliJ process handler.
 */
class KkLldbProcessLauncher : KkNativeDebugProcessLauncher {
    /**
     * 从 debug launch command 创建可停止的 process handler。
     * Creates a killable process handler from the debug launch command.
     */
    override fun start(command: KkNativeDebugCommand.Available): ProcessHandler =
        KillableProcessHandler(
            GeneralCommandLine(command.debugLaunchCommand)
                .withWorkDirectory(command.repositoryRootPath),
        )
}

/**
 * kklang 单文件运行配置，保存要执行的 `.kk` 文件路径。
 * kklang single-file run configuration storing the `.kk` file path to execute.
 */
class KkRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : RunConfigurationBase<RunConfigurationOptions>(project, factory, name) {
    var filePath: String = ""

    /**
     * 返回运行配置编辑器。
     * Returns the run configuration editor.
     */
    override fun getConfigurationEditor(): SettingsEditor<KkRunConfiguration> =
        KkRunConfigurationEditor()

    /**
     * 校验配置的文件路径存在且指向 `.kk` 文件。
     * Validates that the configured file path exists and points to a `.kk` file.
     */
    override fun checkConfiguration() {
        val path = filePath.trim()
        if (path.isEmpty()) {
            throw RuntimeConfigurationException("Select a .kk file to run")
        }
        if (!path.endsWith(".kk")) {
            throw RuntimeConfigurationException("kklang run configuration requires a .kk file")
        }
        if (!Files.isRegularFile(Path.of(path))) {
            throw RuntimeConfigurationException("Configured .kk file does not exist")
        }
    }

    /**
     * 创建执行状态。
     * Creates the execution state.
     */
    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        if (executor.id == DefaultDebugExecutor.EXECUTOR_ID) {
            KkNativeDebugProfileState(filePath)
        } else {
            KkRunProfileState(filePath)
        }

    /**
     * 从 plugin XML state 读取文件路径。
     * Reads the file path from plugin XML state.
     */
    override fun readExternal(element: Element) {
        super.readExternal(element)
        filePath = element.getAttributeValue(FILE_PATH_ATTRIBUTE) ?: ""
    }

    /**
     * 把文件路径写入 plugin XML state。
     * Writes the file path into plugin XML state.
     */
    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute(FILE_PATH_ATTRIBUTE, filePath)
    }

    private companion object {
        private const val FILE_PATH_ATTRIBUTE = "kk-file-path"
    }
}

/**
 * kklang 单文件运行配置编辑器。
 * Editor for kklang single-file run configurations.
 */
class KkRunConfigurationEditor : SettingsEditor<KkRunConfiguration>() {
    private val filePathField = JTextField()
    private val panel = JPanel(BorderLayout()).apply {
        add(JLabel(".kk file:"), BorderLayout.WEST)
        add(filePathField, BorderLayout.CENTER)
    }

    /**
     * 用配置值重置编辑器。
     * Resets the editor from configuration values.
     */
    override fun resetEditorFrom(settings: KkRunConfiguration) {
        filePathField.text = settings.filePath
    }

    /**
     * 把编辑器值写回配置。
     * Applies editor values back to the configuration.
     */
    override fun applyEditorTo(settings: KkRunConfiguration) {
        settings.filePath = filePathField.text.trim()
    }

    /**
     * 创建 Swing 编辑组件。
     * Creates the Swing editor component.
     */
    override fun createEditor(): JComponent = panel
}

/**
 * kklang 单文件运行状态，读取文件并把执行结果写入 console。
 * kklang single-file run state that reads the file and writes execution results into a console.
 */
class KkRunProfileState(
    private val filePath: String,
    private val runService: KkIdeaRunService = KkIdeaRunService(),
    private val nativeDebugCommandService: KkNativeDebugCommandService = KkNativeDebugCommandService(),
) : RunProfileState {
    /**
     * 执行配置并返回 IDEA execution result。
     * Executes the configuration and returns the IDEA execution result.
     */
    override fun execute(executor: Executor, runner: ProgramRunner<*>): com.intellij.execution.ExecutionResult {
        val path = Path.of(filePath)
        if (!Files.isRegularFile(path)) {
            throw ExecutionException("Configured .kk file does not exist: $filePath")
        }

        val result = runService.execute(path.name, path.readText())
        val nativeDebugText = nativeDebugCommandService.commandFor(path.toString()).consoleText()
        val console = KkRunConsole(result.consoleText() + nativeDebugText)
        val processHandler = KkCompletedProcessHandler()
        processHandler.complete(result.exitCode)
        return DefaultExecutionResult(console, processHandler)
    }
}

/**
 * kklang Native debug 状态，启动仓库内 LLDB 会话。
 * kklang Native debug state that starts the in-repository LLDB session.
 */
class KkNativeDebugProfileState(
    private val filePath: String,
    private val commandProvider: (String) -> KkNativeDebugCommand = KkNativeDebugCommandService()::commandFor,
    private val processLauncher: KkNativeDebugProcessLauncher = KkLldbProcessLauncher(),
    private val terminationAttacher: (ProcessHandler) -> Unit = { ProcessTerminatedListener.attach(it) },
) : RunProfileState {
    /**
     * 启动 Native debug process 并返回可输入 LLDB 命令的 console。
     * Starts the Native debug process and returns a console that accepts LLDB commands.
     */
    override fun execute(executor: Executor, runner: ProgramRunner<*>): com.intellij.execution.ExecutionResult {
        val path = Path.of(filePath)
        if (!Files.isRegularFile(path)) {
            throw ExecutionException("Configured .kk file does not exist: $filePath")
        }

        val command = commandProvider(path.toString())
        if (command is KkNativeDebugCommand.Unavailable) {
            throw ExecutionException("Cannot start kklang Native debug session: ${command.reason}")
        }
        command as KkNativeDebugCommand.Available
        val processHandler = processLauncher.start(command)
        terminationAttacher(processHandler)
        val console = KkInteractiveProcessConsole(command.debugConsoleText(), processHandler)
        processHandler.startNotify()
        return DefaultExecutionResult(console, processHandler)
    }
}

/**
 * 已完成的轻量 process handler，用于同步单文件运行结果。
 * Completed lightweight process handler for synchronous single-file run results.
 */
class KkCompletedProcessHandler : NopProcessHandler() {
    /**
     * 标记进程已经以指定退出码结束。
     * Marks the process as finished with the given exit code.
     */
    fun complete(exitCode: Int) {
        startNotify()
        notifyProcessTerminated(exitCode)
    }
}

/**
 * 轻量交互式 process console，把 process 输出追加到文本区并把输入写入 stdin。
 * Lightweight interactive process console that appends process output and writes user input to stdin.
 */
class KkInteractiveProcessConsole(
    initialText: String,
    private val processHandler: ProcessHandler,
) : com.intellij.execution.ui.ExecutionConsole, ProcessListener {
    private val textArea = JTextArea(initialText).apply {
        isEditable = false
    }
    private val inputField = JTextField()
    private val panel = JPanel(BorderLayout()).apply {
        add(textArea, BorderLayout.CENTER)
        add(inputField, BorderLayout.SOUTH)
    }

    init {
        processHandler.addProcessListener(this)
        inputField.addActionListener {
            submitInput(inputField.text)
        }
    }

    val text: String
        get() = textArea.text

    /**
     * 把 process 输出追加到 console 文本区。
     * Appends process output to the console text area.
     */
    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        textArea.append(event.text)
    }

    /**
     * 提交一条 LLDB 命令到 process stdin；空命令不写入。
     * Submits one LLDB command to process stdin; empty commands are not written.
     */
    fun submitInput(command: String) {
        if (command.isEmpty()) {
            return
        }
        textArea.append("> $command\n")
        processHandler.processInput!!.write("$command\n".toByteArray())
        processHandler.processInput!!.flush()
        inputField.text = ""
    }

    /**
     * 返回 console Swing 组件。
     * Returns the console Swing component.
     */
    override fun getComponent(): JComponent = panel

    /**
     * 返回首选 focus 组件。
     * Returns the preferred focus component.
     */
    override fun getPreferredFocusableComponent(): JComponent = inputField

    /**
     * 移除 process listener。
     * Removes the process listener.
     */
    override fun dispose() {
        processHandler.removeProcessListener(this)
    }
}

/**
 * 简单文本 execution console，避免单文件运行依赖外部进程。
 * Simple text execution console so single-file runs do not depend on an external process.
 */
class KkRunConsole(initialText: String) : com.intellij.execution.ui.ExecutionConsole {
    private val textArea = JTextArea(initialText).apply {
        isEditable = false
    }
    private val panel = JPanel(BorderLayout()).apply {
        add(textArea, BorderLayout.CENTER)
    }

    val text: String
        get() = textArea.text

    /**
     * 返回 console Swing 组件。
     * Returns the console Swing component.
     */
    override fun getComponent(): JComponent = panel

    /**
     * 返回首选 focus 组件。
     * Returns the preferred focus component.
     */
    override fun getPreferredFocusableComponent(): JComponent = textArea

    /**
     * 释放 console 资源；当前实现没有外部资源。
     * Disposes console resources; the current implementation owns no external resources.
     */
    override fun dispose() = Unit
}
