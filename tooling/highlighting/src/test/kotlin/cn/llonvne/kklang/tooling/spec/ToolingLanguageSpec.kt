package cn.llonvne.kklang.tooling.spec

/**
 * tooling 规范 DSL 的 marker，防止 builder scope 意外混用。
 * Marker for the tooling spec DSL that prevents accidental builder scope mixing.
 */
@DslMarker
annotation class ToolingSpecDsl

/**
 * `.kk` 工具链的可执行 DSL 规范快照。
 * Executable DSL spec snapshot for `.kk` tooling.
 */
data class ToolingLanguageSpec(
    val fileExtension: String,
    val highlightCategories: List<HighlightCategorySpec>,
    val lspFeatures: List<String>,
    val ideaFeatures: List<String>,
)

/**
 * 高亮分类规范项。
 * Highlight category spec item.
 */
data class HighlightCategorySpec(
    val category: String,
    val tokenKinds: List<String>,
)

/**
 * 创建 tooling DSL 规范。
 * Creates the tooling DSL spec.
 */
fun toolingSpec(block: ToolingLanguageSpecBuilder.() -> Unit): ToolingLanguageSpec =
    ToolingLanguageSpecBuilder().apply(block).build()

/**
 * tooling language 规范 builder。
 * Tooling language spec builder.
 */
@ToolingSpecDsl
class ToolingLanguageSpecBuilder {
    private var fileExtension = ""
    private val highlightCategories = mutableListOf<HighlightCategorySpec>()
    private val lspFeatures = mutableListOf<String>()
    private val ideaFeatures = mutableListOf<String>()

    /**
     * 记录源文件扩展名。
     * Records the source file extension.
     */
    fun fileExtension(extension: String) {
        fileExtension = extension
    }

    /**
     * 进入高亮分类规范 builder。
     * Enters the highlight category spec builder.
     */
    fun highlighting(block: HighlightingSpecBuilder.() -> Unit) {
        HighlightingSpecBuilder(highlightCategories).apply(block)
    }

    /**
     * 记录一个 LSP feature。
     * Records one LSP feature.
     */
    fun lspFeature(feature: String) {
        lspFeatures += feature
    }

    /**
     * 记录一个 IDEA plugin feature。
     * Records one IDEA plugin feature.
     */
    fun ideaFeature(feature: String) {
        ideaFeatures += feature
    }

    /**
     * 构造不可变 tooling 规范。
     * Builds the immutable tooling spec.
     */
    fun build(): ToolingLanguageSpec =
        ToolingLanguageSpec(
            fileExtension = fileExtension,
            highlightCategories = highlightCategories.toList(),
            lspFeatures = lspFeatures.toList(),
            ideaFeatures = ideaFeatures.toList(),
        )
}

/**
 * 高亮分类规范 builder。
 * Highlight category spec builder.
 */
@ToolingSpecDsl
class HighlightingSpecBuilder(private val categories: MutableList<HighlightCategorySpec>) {
    /**
     * 记录一个高亮分类及其覆盖的 token kinds。
     * Records one highlight category and the token kinds it covers.
     */
    fun category(name: String, vararg tokenKinds: String) {
        categories += HighlightCategorySpec(category = name, tokenKinds = tokenKinds.toList())
    }
}

val toolingLanguageSpec = toolingSpec {
    fileExtension("kk")

    highlighting {
        category("keyword", "val")
        category("identifier", "identifier")
        category("integer", "integer")
        category("operator", "plus", "minus", "star", "slash", "equals")
        category("delimiter", "left_paren", "right_paren", "semicolon")
        category("whitespace", "whitespace")
        category("unknown", "unknown")
        category("eof", "eof")
    }

    lspFeature("stdio-json-rpc")
    lspFeature("textDocumentSync")
    lspFeature("publishDiagnostics")
    lspFeature("semanticTokensFull")

    ideaFeature("kk-file-type")
    ideaFeature("syntax-highlighter")
    ideaFeature("minimal-psi")
    ideaFeature("diagnostic-annotator")
    ideaFeature("installable-plugin-zip")
}
