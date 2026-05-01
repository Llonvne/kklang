plugins {
    base
    kotlin("jvm") version "2.3.20" apply false
    kotlin("multiplatform") version "2.3.20" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.8" apply false
}

group = "cn.llonvne"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.register("test") {
    dependsOn(":compiler:core:jvmTest")
    dependsOn(":tooling:highlighting:test")
    dependsOn(":tooling:lsp:test")
    dependsOn(":tooling:idea-plugin:test")
}

tasks.register("koverVerify") {
    dependsOn(":compiler:core:koverVerify")
    dependsOn(":tooling:highlighting:koverVerify")
    dependsOn(":tooling:lsp:koverVerify")
    dependsOn(":tooling:idea-plugin:koverVerify")
}

tasks.check {
    dependsOn(tasks.named("test"))
    dependsOn(tasks.named("koverVerify"))
    dependsOn(":compiler:core:check")
    dependsOn(":runtime:kn:check")
    dependsOn(":tooling:highlighting:check")
    dependsOn(":tooling:lsp:check")
    dependsOn(":tooling:lsp:installDist")
    dependsOn(":tooling:idea-plugin:check")
    dependsOn(":tooling:idea-plugin:buildPluginZip")
}
