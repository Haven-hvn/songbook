plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:9.3.3")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    implementation("com.google.dagger:hilt-android-gradle-plugin:2.60.1")
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.12")
}

// Every file under src/main/kotlin/*.gradle.kts is a precompiled script plugin; its id is the file
// name. Do not also register them in a `gradlePlugin { }` block.
//
//   ipfsmedia.android.application   the :app module
//   ipfsmedia.android.library       Android library without Compose (the copied core-* modules)
//   ipfsmedia.android.compose       library + Compose + Material 3 Expressive
//   ipfsmedia.android.feature       compose + the modules every feature screen builds on
//
// The Compose compiler, KSP and Hilt plugins are applied by each module itself, next to the
// convention plugin, exactly as haven-mobile does it.
