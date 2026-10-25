pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Tesseract4Android（离线手写 OCR）只发布在 JitPack
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "WordEcho"
include(":app")
