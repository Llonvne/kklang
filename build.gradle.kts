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
}

tasks.register("koverVerify") {
    dependsOn(":compiler:core:koverVerify")
}

tasks.check {
    dependsOn(tasks.named("test"))
    dependsOn(tasks.named("koverVerify"))
    dependsOn(":runtime:kn:check")
}
