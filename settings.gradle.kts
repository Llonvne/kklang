plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "kklang"
include(":compiler:core")
include(":runtime:kn")
include(":tooling:highlighting")
include(":tooling:lsp")
include(":tooling:idea-plugin")
