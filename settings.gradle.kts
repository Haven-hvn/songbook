pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "9.3.3"
        id("com.android.library") version "9.3.3"
        id("org.jetbrains.kotlin.android") version "2.3.21"
        id("org.jetbrains.kotlin.jvm") version "2.3.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21"
        id("com.google.devtools.ksp") version "2.3.12"
        id("com.google.dagger.hilt.android") version "2.60.1"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Reown (WalletConnect) pulls a few transitive artifacts from JitPack.
        maven(url = "https://jitpack.io")
        maven(url = "https://central.sonatype.com/repository/maven-snapshots/")
    }
}

rootProject.name = "googlebook-ipfsmedia"

include(":app")

// New in this repo.
include(":core-design")
include(":core-navigation")
include(":core-library")
include(":core-userdata")
include(":core-social")
include(":core-player")

// Copied from haven-mobile (see docs/UPSTREAM.md).
include(":core-domain")
include(":core-wallet")
include(":core-haven-aol")
include(":core-arkiv")
include(":core-collections")
include(":core-crypto")
include(":core-attestation")
include(":core-cache")
include(":core-cache-mirror")
include(":core-security")

include(":feature-onboarding")
include(":feature-identity")
include(":feature-home")
include(":feature-collection")
include(":feature-album")
include(":feature-video")
include(":feature-datadao")
include(":feature-discussion")
include(":feature-uploader")
include(":feature-search")
include(":feature-playlists")
include(":feature-history")
include(":feature-nowplaying")
include(":feature-settings")

includeBuild("build-logic")
