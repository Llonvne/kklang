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
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.icons.AllIcons
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
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
    AllIcons.RunConfigurations.Application,
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
class KkRunConfigurationProducer : LazyRunConfigurationProducer<KkRunConfiguration>() {
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
    if (file.fileType != KkFileType) {
        return null
    }
    val virtualFile = file.virtualFile ?: return null
    if (!virtualFile.path.endsWith(".kk")) {
        return null
    }
    return file
}

/**
 * 规范化本地文件路径以便比较运行配置来源。
 * Normalizes a local file path for comparing run-configuration origins.
 */
private fun String.normalizedPath(): String =
    Path.of(this).normalize().toString()

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
        KkRunProfileState(filePath)

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
        val console = KkRunConsole(result.consoleText())
        val processHandler = KkCompletedProcessHandler()
        processHandler.complete(result.exitCode)
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
