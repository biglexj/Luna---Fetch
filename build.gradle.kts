plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
}

tasks.register("dev") {
    group = "application"
    description = "Ejecuta la aplicación de escritorio en modo desarrollo"
    dependsOn(":composeApp:run")
}

tasks.register("devAndroid") {
    group = "application"
    description = "Compila e instala la app en el dispositivo Android conectado"
    dependsOn(":composeApp:installDebug")
}

tasks.register<Exec>("releaseLocal") {
    group = "publishing"
    description = "Compila y empaqueta releases locales de Windows y Android (.exe y .apk)"
    workingDir = rootDir
    commandLine("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "./scripts/build/build-release.ps1", "-LocalOnly")
}

tasks.register<Exec>("releasePublish") {
    group = "publishing"
    description = "Pipeline completo de release oficial (Git tag, GitHub Release, SHA256)"
    workingDir = rootDir
    commandLine("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "./scripts/build/build-release.ps1")
}
