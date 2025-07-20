plugins {
    java
    application
    id("com.gradleup.shadow") version "9.0.0-rc1"
}

group = "net.minestom.jam"
version = "1.0"
application.mainClass = "net.minestom.jam.Main"

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.minestom:minestom:2025.07.11-1.21.7")
    implementation("org.tinylog:tinylog-impl:2.8.0-M1")
    implementation("org.tinylog:slf4j-tinylog:2.8.0-M1")
    implementation("de.fabmax:physx-jni:2.5.1")              // Changed to older version with cuda support
    runtimeOnly("de.fabmax:physx-jni:2.5.1:natives-windows") // Changed to older version with cuda support
    implementation("org.lwjgl:lwjgl:3.3.6")
    runtimeOnly("org.lwjgl:lwjgl::natives-windows")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}