pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.4.2"
        id("com.google.gms.google-services") version "4.4.2"
        id("org.jetbrains.kotlin.android") version "1.9.0"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // jcenter() 필요시 아래 주석 해제
        // jcenter()
    }
}

rootProject.name = "term_project"
include(":app")
