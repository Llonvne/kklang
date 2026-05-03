@file:OptIn(kotlin.io.path.ExperimentalPathApi::class)

package cn.llonvne.kklang.idea

import cn.llonvne.kklang.execution.ExecutionValue
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.Location
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.icons.AllIcons
import com.intellij.mock.MockProject
import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.TimerListener
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile
import org.jdom.Element
import java.awt.Container
import java.lang.reflect.Proxy
import javax.swing.JTextField
import sun.misc.Unsafe
import kotlin.io.path.createTempFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 覆盖 IDEA 单文件运行服务、运行配置和同步 execution state。
 * Covers the IDEA single-file run service, run configuration, and synchronous execution state.
 */
class KkRunConfigurationTest {
    /**
     * 验证 run service 成功执行源码并渲染 stdout 与最终值。
     * Verifies that the run service executes source successfully and renders stdout plus the final value.
     */
    @Test
    fun `run service executes source and renders success console text`() {
        val result = KkIdeaRunService().execute("main.kk", "print(\"hello\")")

        assertIs<KkIdeaRunResult.Success>(result)
        assertEquals("hello", result.stdout)
        assertEquals("Unit", result.valueText)
        assertEquals(0, result.exitCode)
        assertEquals("hello\nResult: Unit\nProcess finished with exit code 0\n", result.consoleText())
    }

    /**
     * 验证 run service 失败时保留 diagnostics 并渲染失败 console 文本。
     * Verifies that the run service preserves diagnostics on failure and renders failure console text.
     */
    @Test
    fun `run service renders failure diagnostics`() {
        val result = KkIdeaRunService().execute("main.kk", "1 / 0")

        assertIs<KkIdeaRunResult.Failure>(result)
        assertEquals("", result.stdout)
        assertEquals(listOf("EXEC002"), result.diagnostics.map { it.code })
        assertEquals(1, result.exitCode)
        assertEquals("EXEC002: division by zero\nProcess finished with exit code 1\n", result.consoleText())
    }

    /**
     * 验证 run service 拒绝空源码名称并用引号显示 String 结果。
     * Verifies that the run service rejects blank source names and displays String results with quotes.
     */
    @Test
    fun `run service validates source name and displays string results`() {
        val result = KkIdeaRunService().execute("main.kk", "\"hello\"")
        val intResult = KkIdeaRunService().execute("main.kk", "1 + 2")

        assertIs<KkIdeaRunResult.Success>(result)
        assertIs<KkIdeaRunResult.Success>(intResult)
        assertEquals("\"hello\"", result.valueText)
        assertEquals("3", intResult.valueText)
        assertEquals("Result: \"hello\"\nProcess finished with exit code 0\n", result.consoleText())
        assertEquals(
            "hello\nResult: Unit\nProcess finished with exit code 0\n",
            KkIdeaRunResult.Success(stdout = "hello\n", valueText = "Unit").consoleText(),
        )
        assertFailsWith<IllegalArgumentException> {
            KkIdeaRunService().execute("", "1")
        }
    }

    /**
     * 验证 console helper 的 stdout 换行分支和 value 显示分支。
     * Verifies the console helper stdout newline branches and value display branches.
     */
    @Test
    fun `run console helpers cover direct formatting branches`() {
        val service = KkIdeaRunService()

        assertEquals("tail", buildConsoleText("", "tail"))
        assertEquals("hello\ntail", buildConsoleText("hello", "tail"))
        assertEquals("hello\ntail", buildConsoleText("hello\n", "tail"))
        assertEquals("7", ideaText(service, ExecutionValue.Int64(7)))
        assertEquals("\"hello\"", ideaText(service, ExecutionValue.String("hello")))
        assertEquals("Unit", ideaText(service, ExecutionValue.Unit))
    }

    /**
     * 验证 run configuration type 和 factory 创建 kklang 配置。
     * Verifies that the run configuration type and factory create kklang configurations.
     */
    @Test
    fun `run configuration type exposes factory`() {
        val project = mockProject()
        try {
            val factory = KkRunConfigurationType.configurationFactory
            val producer = KkRunConfigurationProducer()
            val configuration = factory.createTemplateConfiguration(project)

            assertEquals("kklang.single.file", KkRunConfigurationType.id)
            assertEquals("kklang.single.file.factory", factory.id)
            assertEquals("kklang", KkRunConfigurationType.displayName)
            assertSame(KkIcons.Language, KkRunConfigurationType.icon)
            assertEquals(1, KkRunConfigurationType.configurationFactories.size)
            assertSame(factory, producer.configurationFactory)
            assertIs<DumbAware>(producer)
            assertIs<KkRunConfiguration>(configuration)
        } finally {
            project.dispose()
        }
    }

    /**
     * 验证 producer 从当前 `.kk` PSI 文件填充路径、名称和 source element。
     * Verifies that the producer fills path, name, and source element from the current `.kk` PSI file.
     */
    @Test
    fun `run configuration producer populates configuration from kk file context`() {
        val project = mockProject()
        val file = psiFile(project, LightVirtualFile("/tmp/main.kk", KkFileType, "1"), KkFileType)
        try {
            val producer = KkRunConfigurationProducer()
            val configuration = KkRunConfiguration(project, KkRunConfigurationType.configurationFactory, "sample")
            val sourceElement = Ref.create<PsiElement>()

            assertTrue(producerSetup(producer, configuration, configurationContext(project, file), sourceElement))
            assertEquals(file.virtualFile.path, configuration.filePath)
            assertEquals("Run main.kk", configuration.name)
            assertSame(file, sourceElement.get())
        } finally {
            project.dispose()
        }
    }

    /**
     * 验证 producer 拒绝非 `.kk`、无 virtual file 和无 containing file 的 context。
     * Verifies that the producer rejects non-`.kk`, missing-virtual-file, and missing-containing-file contexts.
     */
    @Test
    fun `run configuration producer rejects unsupported contexts`() {
        val project = mockProject()
        val producer = KkRunConfigurationProducer()
        val configuration = KkRunConfiguration(project, KkRunConfigurationType.configurationFactory, "sample")
        val sourceElement = Ref.create<PsiElement>()
        val plainFile = psiFile(project, LightVirtualFile("/tmp/main.kk", PlainTextFileType.INSTANCE, "1"), PlainTextFileType.INSTANCE)
        val wrongExtension = psiFile(project, LightVirtualFile("/tmp/main.txt", KkFileType, "1"), KkFileType)
        val missingVirtualFile = psiFile(project, null, KkFileType)
        val missingContainingFile = psiElement(project, null)
        val missingLocation = configurationContextWithoutLocation()

        try {
            assertFalse(producerSetup(producer, configuration, configurationContext(project, plainFile), sourceElement))
            assertFalse(producerSetup(producer, configuration, configurationContext(project, wrongExtension), sourceElement))
            assertFalse(producerSetup(producer, configuration, configurationContext(project, missingVirtualFile), sourceElement))
            assertFalse(producerSetup(producer, configuration, configurationContext(project, missingContainingFile), sourceElement))
            assertFalse(producerSetup(producer, configuration, missingLocation, sourceElement))
            assertEquals("", configuration.filePath)
        } finally {
            project.dispose()
        }
    }

    /**
     * 验证 producer 能识别已指向当前文件的运行配置。
     * Verifies that the producer recognizes configurations that already point at the current file.
     */
    @Test
    fun `run configuration producer matches existing configuration by normalized path`() {
        val project = mockProject()
        val producer = KkRunConfigurationProducer()
        val configuration = KkRunConfiguration(project, KkRunConfigurationType.configurationFactory, "sample")
        val file = psiFile(project, LightVirtualFile("/tmp/kklang/../main.kk", KkFileType, "1"), KkFileType)
        val context = configurationContext(project, file)
        val plainFile = psiFile(project, LightVirtualFile("/tmp/main.kk", PlainTextFileType.INSTANCE, "1"), PlainTextFileType.INSTANCE)

        try {
            configuration.filePath = "/tmp/main.kk"
            assertTrue(producer.isConfigurationFromContext(configuration, context))
            configuration.filePath = "/tmp/other.kk"
            assertFalse(producer.isConfigurationFromContext(configuration, context))
            assertFalse(producer.isConfigurationFromContext(configuration, configurationContext(project, plainFile)))
        } finally {
            project.dispose()
        }
    }

    /**
     * 验证 run line marker 使用 IDEA 标准 current-file 入口标记 `.kk` 文件。
     * Verifies that the run line marker marks `.kk` files through IDEA's standard current-file entry.
     */
    @Test
    fun `run line marker exposes standard current file entry for kk files`() {
        val project = mockProject()
        val kkFile = psiFile(project, LightVirtualFile("/tmp/main.kk", KkFileType, "1"), KkFileType)
        val plainFile = psiFile(project, LightVirtualFile("/tmp/main.txt", PlainTextFileType.INSTANCE, "1"), PlainTextFileType.INSTANCE)
        val anchor = psiElement(project, kkFile, textOffset = 0)
        val laterElement = psiElement(project, kkFile, textOffset = 1)
        val missingContainingFile = psiElement(project, null)

        try {
            withMockApplication {
                val contributor = KkRunLineMarkerContributor()

                assertIs<DumbAware>(contributor)
                assertTrue(contributor.producesAllPossibleConfigurations(kkFile))
                assertFalse(contributor.producesAllPossibleConfigurations(plainFile))
                val info = assertNotNull(contributor.getInfo(anchor))
                val fileInfo = assertNotNull(contributor.getInfo(kkFile))
                assertEquals(AllIcons.RunConfigurations.TestState.Run, info.icon)
                assertEquals("Run main.kk", info.tooltipProvider.apply(anchor))
                assertEquals("Run main.kk", fileInfo.tooltipProvider.apply(kkFile))
                assertNull(contributor.getInfo(laterElement))
                assertNull(contributor.getInfo(plainFile))
                assertNull(contributor.getInfo(missingContainingFile))
            }
        } finally {
            project.dispose()
        }
    }

    /**
     * 验证 Native debug command service 从仓库根目录生成 build 和 LLDB 命令。
     * Verifies that the Native debug command service builds build and LLDB commands from the repository root.
     */
    @Test
    fun `native debug command service builds commands from repo root`() {
        val root = createTempDirectory()
        try {
            root.resolve("settings.gradle.kts").writeText("rootProject.name = \"kklang\"")
            root.resolve("runtime").resolve("kn").createDirectories()
            val source = root.resolve("samples").resolve("main.kk")
            source.parent.createDirectories()
            source.writeText("1")

            val command = assertIs<KkNativeDebugCommand.Available>(
                KkNativeDebugCommandService().commandFor(source.pathString),
            )

            assertEquals(source.toAbsolutePath().normalize().pathString, command.sourceFilePath)
            assertEquals(root.toAbsolutePath().normalize().pathString, command.repositoryRootPath)
            assertEquals(
                root.resolve("runtime/kn/build/bin/host/kkrunDebugExecutable/kkrun.kexe")
                    .toAbsolutePath()
                    .normalize()
                    .pathString,
                command.executablePath,
            )
            assertTrue(command.buildCommand.contains(":runtime:kn:linkKkrunDebugExecutableHost"))
            assertTrue(command.printCommand.contains(":runtime:kn:printRuntimeSingleFileDebugCommand"))
            assertTrue(command.lldbCommand.contains("lldb --"))
            assertTrue(command.consoleText().contains("Native debug executable:"))
        } finally {
            root.deleteRecursively()
        }
    }

    /**
     * 验证 Native debug command service 在没有完整仓库标记时不生成命令。
     * Verifies that the Native debug command service does not create commands without complete repository markers.
     */
    @Test
    fun `native debug command service rejects unsupported roots`() {
        val root = createTempDirectory()
        val incompleteRoot = createTempDirectory()
        try {
            val source = root.resolve("main.kk")
            source.writeText("1")
            incompleteRoot.resolve("settings.gradle.kts").writeText("rootProject.name = \"kklang\"")
            val incompleteSource = incompleteRoot.resolve("main.kk")
            incompleteSource.writeText("1")

            val unavailable = assertIs<KkNativeDebugCommand.Unavailable>(
                KkNativeDebugCommandService().commandFor(source.pathString),
            )
            assertEquals("", unavailable.consoleText())
            assertIs<KkNativeDebugCommand.Unavailable>(
                KkNativeDebugCommandService().commandFor(incompleteSource.pathString),
            )
            assertFailsWith<IllegalArgumentException> {
                KkNativeDebugCommandService().commandFor("")
            }
        } finally {
            root.deleteRecursively()
            incompleteRoot.deleteRecursively()
        }
    }

    /**
     * 验证 run configuration 校验 `.kk` 文件路径。
     * Verifies that the run configuration validates `.kk` file paths.
     */
    @Test
    fun `run configuration validates file path`() {
        val project = mockProject()
        val kkFile = createTempFile(suffix = ".kk")
        val txtFile = createTempFile(suffix = ".txt")
        try {
            kkFile.writeText("1")
            txtFile.writeText("1")
            val configuration = KkRunConfiguration(project, KkRunConfigurationType.configurationFactory, "sample")

            assertFailsWith<RuntimeConfigurationException> { configuration.checkConfiguration() }
            configuration.filePath = txtFile.toString()
            assertFailsWith<RuntimeConfigurationException> { configuration.checkConfiguration() }
            configuration.filePath = kkFile.resolveSibling("missing.kk").toString()
            assertFailsWith<RuntimeConfigurationException> { configuration.checkConfiguration() }
            configuration.filePath = kkFile.toString()
            configuration.checkConfiguration()
        } finally {
            kkFile.deleteIfExists()
            txtFile.deleteIfExists()
            project.dispose()
        }
    }

    /**
     * 验证 run configuration XML 持久化和 editor 应用。
     * Verifies run configuration XML persistence and editor application.
     */
    @Test
    fun `run configuration persists file path and editor applies changes`() {
        val project = mockProject()
        try {
            val configuration = KkRunConfiguration(project, KkRunConfigurationType.configurationFactory, "sample")
            configuration.filePath = "/tmp/main.kk"
            val element = Element("configuration")

            configuration.writeExternal(element)
            val restored = KkRunConfiguration(project, KkRunConfigurationType.configurationFactory, "sample")
            restored.readExternal(element)
            assertEquals("/tmp/main.kk", restored.filePath)

            val empty = KkRunConfiguration(project, KkRunConfigurationType.configurationFactory, "sample")
            empty.readExternal(Element("configuration"))
            assertEquals("", empty.filePath)

            val editor = configuration.configurationEditor
            editor.resetFrom(configuration)
            val field = assertNotNull(findTextField(editor.component))
            field.text = " /tmp/next.kk "
            editor.applyTo(restored)
            assertEquals("/tmp/next.kk", restored.filePath)
            editor.dispose()
        } finally {
            project.dispose()
        }
    }

    /**
     * 验证 run profile state 从文件执行并返回已完成 process handler。
     * Verifies that the run profile state executes from a file and returns a completed process handler.
     */
    @Test
    fun `run profile state executes file`() {
        val kkFile = createTempFile(suffix = ".kk")
        val project = mockProject()
        try {
            kkFile.writeText("print(\"hello\")")
            val configuration = KkRunConfiguration(project, KkRunConfigurationType.configurationFactory, "sample")
            configuration.filePath = kkFile.toString()
            val state = assertIs<KkRunProfileState>(configuration.getState(TestExecutor, ExecutionEnvironment()))
            val result = state.execute(TestExecutor, TestRunner)
            val console = assertIs<KkRunConsole>(result.executionConsole)

            assertEquals("hello\nResult: Unit\nProcess finished with exit code 0\n", console.text)
            assertEquals(0, result.processHandler.exitCode)
            assertNotNull(console.component)
            assertIs<javax.swing.JTextArea>(console.preferredFocusableComponent)
            console.dispose()
        } finally {
            kkFile.deleteIfExists()
            project.dispose()
        }
    }

    /**
     * 验证 run profile state 在仓库内文件运行后追加 Native debug 命令提示。
     * Verifies that the run profile state appends Native debug command hints after running a file inside the repo.
     */
    @Test
    fun `run profile state appends native debug command for repo files`() {
        val root = createTempDirectory()
        val project = mockProject()
        try {
            root.resolve("settings.gradle.kts").writeText("rootProject.name = \"kklang\"")
            root.resolve("runtime").resolve("kn").createDirectories()
            val kkFile = root.resolve("main.kk")
            kkFile.writeText("1 + 2")
            val state = KkRunProfileState(kkFile.pathString)
            val result = state.execute(TestExecutor, TestRunner)
            val console = assertIs<KkRunConsole>(result.executionConsole)

            assertTrue(console.text.contains("Result: 3\nProcess finished with exit code 0\n"))
            assertTrue(console.text.contains("Native debug executable:"))
            assertTrue(console.text.contains("Native debug build:"))
            assertTrue(console.text.contains("Native debug LLDB:"))
            console.dispose()
        } finally {
            root.deleteRecursively()
            project.dispose()
        }
    }

    /**
     * 验证 run profile state 在文件缺失时抛出 ExecutionException。
     * Verifies that the run profile state throws ExecutionException when the file is missing.
     */
    @Test
    fun `run profile state rejects missing file`() {
        assertFailsWith<ExecutionException> {
            KkRunProfileState("/tmp/kklang-missing-file.kk").execute(TestExecutor, TestRunner)
        }
    }

    /**
     * 创建 IDEA MockProject。
     * Creates an IDEA MockProject.
     */
    private fun mockProject(): MockProject =
        MockProject(null, Disposable {}).apply {
            registerService(ProjectFileIndex::class.java, projectFileIndex())
        }

    /**
     * 创建测试用 ProjectFileIndex service。
     * Creates the ProjectFileIndex service for tests.
     */
    private fun projectFileIndex(): ProjectFileIndex =
        Proxy.newProxyInstance(
            ProjectFileIndex::class.java.classLoader,
            arrayOf(ProjectFileIndex::class.java),
        ) { _, method, _ -> defaultValue(method.returnType) } as ProjectFileIndex

    /**
     * 返回 Java proxy 方法所需的默认值。
     * Returns default values needed by Java proxy methods.
     */
    private fun defaultValue(type: Class<*>): Any? =
        when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Double.TYPE -> 0.0
            java.lang.Float.TYPE -> 0.0f
            java.lang.Character.TYPE -> '\u0000'
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Byte.TYPE -> 0.toByte()
            else -> null
        }

    /**
     * 在测试范围内安装最小 IDEA application 和 ActionManager。
     * Installs a minimal IDEA application and ActionManager for the test scope.
     */
    private fun withMockApplication(block: () -> Unit) {
        val disposable = Disposer.newDisposable()
        val application = MockApplication(disposable)
        ApplicationManager.setApplication(application, disposable)
        application.extensionArea.registerExtensionPoint(
            Executor.EXECUTOR_EXTENSION_NAME,
            Executor::class.java.name,
            ExtensionPoint.Kind.INTERFACE,
            disposable,
        )
        application.registerService(ActionManager::class.java, TestActionManager)
        try {
            block()
        } finally {
            Disposer.dispose(disposable)
        }
    }

    /**
     * 在 editor 组件树中查找文本框。
     * Finds the text field inside the editor component tree.
     */
    private fun findTextField(component: java.awt.Component): JTextField? {
        if (component is JTextField) {
            return component
        }
        if (component is Container) {
            for (child in component.components) {
                val found = findTextField(child)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    /**
     * 通过反射调用私有 console formatter。
     * Calls the private console formatter through reflection.
     */
    private fun buildConsoleText(stdout: String, tail: String): String {
        val method = Class.forName("cn.llonvne.kklang.idea.KkRunConfigurationKt").getDeclaredMethod(
            "buildConsoleText",
            String::class.java,
            Function1::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, stdout, fun(builder: StringBuilder) {
            builder.append(tail)
        }) as String
    }

    /**
     * 通过反射调用私有 IDEA value formatter。
     * Calls the private IDEA value formatter through reflection.
     */
    private fun ideaText(service: KkIdeaRunService, value: ExecutionValue): String {
        val method = KkIdeaRunService::class.java.getDeclaredMethod("ideaText", ExecutionValue::class.java)
        method.isAccessible = true
        return method.invoke(service, value) as String
    }

    /**
     * 调用 producer 的 protected setup 方法以覆盖 IDEA context glue。
     * Invokes the producer's protected setup method to cover the IDEA context glue.
     */
    private fun producerSetup(
        producer: KkRunConfigurationProducer,
        configuration: KkRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val method = KkRunConfigurationProducer::class.java.getDeclaredMethod(
            "setupConfigurationFromContext",
            KkRunConfiguration::class.java,
            ConfigurationContext::class.java,
            Ref::class.java,
        )
        method.isAccessible = true
        return method.invoke(producer, configuration, context, sourceElement) as Boolean
    }

    /**
     * 创建最小 ConfigurationContext，避免启动完整 IDE test fixture。
     * Creates a minimal ConfigurationContext without starting the full IDE test fixture.
     */
    private fun configurationContext(project: Project, psiElement: PsiElement): ConfigurationContext {
        val context = unsafe().allocateInstance(ConfigurationContext::class.java) as ConfigurationContext
        val field = ConfigurationContext::class.java.getDeclaredField("myLocation")
        field.isAccessible = true
        field.set(context, TestLocation(project, psiElement))
        return context
    }

    /**
     * 创建没有 PSI location 的最小 ConfigurationContext。
     * Creates a minimal ConfigurationContext without a PSI location.
     */
    private fun configurationContextWithoutLocation(): ConfigurationContext {
        val context = unsafe().allocateInstance(ConfigurationContext::class.java) as ConfigurationContext
        val field = ConfigurationContext::class.java.getDeclaredField("myLocation")
        field.isAccessible = true
        field.set(context, null)
        return context
    }

    /**
     * 创建测试用 PSI file proxy。
     * Creates a PSI file proxy for tests.
     */
    private fun psiFile(project: Project, virtualFile: VirtualFile?, fileType: com.intellij.openapi.fileTypes.FileType): PsiFile =
        proxyPsi(project, virtualFile, fileType) as PsiFile

    /**
     * 创建测试用 PSI element proxy。
     * Creates a PSI element proxy for tests.
     */
    private fun psiElement(project: Project, containingFile: PsiFile?, textOffset: Int = 0): PsiElement =
        Proxy.newProxyInstance(
            PsiElement::class.java.classLoader,
            arrayOf(PsiElement::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getProject" -> project
                "getContainingFile" -> containingFile
                "getTextOffset" -> textOffset
                "isValid" -> true
                "toString" -> "test kklang psi element"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> defaultValue(method.returnType)
            }
        } as PsiElement

    /**
     * 创建可同时模拟 PsiElement/PsiFile 访问的 proxy。
     * Creates a proxy that can simulate PsiElement/PsiFile access.
     */
    private fun proxyPsi(
        project: Project,
        virtualFile: VirtualFile?,
        fileType: com.intellij.openapi.fileTypes.FileType,
        containingFile: PsiFile? = null,
    ): PsiElement =
        Proxy.newProxyInstance(
            PsiFile::class.java.classLoader,
            arrayOf(PsiFile::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getProject" -> project
                "getContainingFile" -> containingFile ?: proxy
                "getOriginalFile" -> proxy
                "getFileType" -> fileType
                "getVirtualFile" -> virtualFile
                "getName" -> virtualFile?.name ?: "missing.kk"
                "getTextOffset" -> 0
                "isValid" -> true
                "toString" -> "test kklang psi file"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> defaultValue(method.returnType)
            }
        } as PsiElement

    /**
     * 读取 Unsafe 以构造不依赖 IDE service 的测试 context。
     * Reads Unsafe to construct a test context that does not depend on IDE services.
     */
    private fun unsafe(): Unsafe {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        return field.get(null) as Unsafe
    }

    /**
     * 测试用 Location，只暴露当前 PSI element 和 project。
     * Test Location exposing only the current PSI element and project.
     */
    private class TestLocation(
        private val project: Project,
        private val psiElement: PsiElement,
    ) : Location<PsiElement>() {
        override fun getPsiElement(): PsiElement = psiElement
        override fun getProject(): Project = project
        override fun <T : PsiElement> getAncestors(clazz: Class<T>, strict: Boolean): Iterator<Location<T>> =
            emptyList<Location<T>>().iterator()
        override fun getModule(): Module? = null
    }

    /**
     * 测试用 Executor，单文件 state 不读取其中信息。
     * Test Executor; the single-file state does not read data from it.
     */
    private object TestExecutor : Executor() {
        override fun getToolWindowId(): String = "Run"
        override fun getToolWindowIcon() = AllIcons.RunConfigurations.Application
        override fun getIcon() = AllIcons.RunConfigurations.Application
        override fun getDisabledIcon() = AllIcons.RunConfigurations.Application
        override fun getDescription(): String = "Run kklang test"
        override fun getActionName(): String = "Run"
        override fun getId(): String = "Run"
        override fun getStartActionText(): String = "Run"
        override fun getContextActionId(): String = "RunKk"
        override fun getHelpId(): String? = null
    }

    /**
     * 测试用 ActionManager，提供 run line marker action 查询所需的最小动作表面。
     * Test ActionManager providing the minimal action surface needed by run-line-marker action lookup.
     */
    private object TestActionManager : ActionManager() {
        private val extraActions = DefaultActionGroup()

        override fun createActionPopupMenu(place: String, group: ActionGroup): ActionPopupMenu =
            throw UnsupportedOperationException("popup menus are not needed in kklang tests")

        override fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean): ActionToolbar =
            throw UnsupportedOperationException("toolbars are not needed in kklang tests")

        override fun getAction(id: String): AnAction = extraActions

        override fun getId(action: AnAction): String = "test-action"

        override fun registerAction(id: String, action: AnAction) = Unit

        override fun registerAction(id: String, action: AnAction, pluginId: PluginId?) = Unit

        override fun unregisterAction(id: String) = Unit

        override fun replaceAction(id: String, action: AnAction) = Unit

        @Deprecated("Overrides deprecated IntelliJ ActionManager API for test compatibility.")
        override fun getActionIds(prefix: String): Array<String> = emptyArray()

        override fun getActionIdList(prefix: String): List<String> = emptyList()

        override fun isGroup(id: String): Boolean = id == "RunLineMarkerExtraActions"

        override fun getActionOrStub(id: String): AnAction = extraActions

        override fun addTimerListener(listener: TimerListener) = Unit

        override fun removeTimerListener(listener: TimerListener) = Unit

        override fun tryToExecute(
            action: AnAction,
            inputEvent: java.awt.event.InputEvent?,
            contextComponent: java.awt.Component?,
            place: String?,
            now: Boolean,
        ): ActionCallback = ActionCallback.DONE

        @Deprecated("Overrides deprecated IntelliJ ActionManager API for test compatibility.")
        override fun addAnActionListener(listener: AnActionListener, parentDisposable: Disposable) = Unit

        override fun getKeyboardShortcut(actionId: String): KeyboardShortcut? = null
    }

    /**
     * 测试用 ProgramRunner，单文件 state 不读取其中信息。
     * Test ProgramRunner; the single-file state does not read data from it.
     */
    private object TestRunner : ProgramRunner<RunnerSettings> {
        override fun getRunnerId(): String = "KkTestRunner"
        override fun canRun(executorId: String, profile: com.intellij.execution.configurations.RunProfile): Boolean = true
        override fun execute(environment: ExecutionEnvironment) = Unit
    }
}
