plugins {
    id("ipfsmedia.android.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// What every feature screen is built from. Feature modules add only what is specific to them,
// and apply the Compose compiler plugin themselves (its Gradle plugin is not on this build's
// classpath, exactly as in haven-mobile).
dependencies {
    add("implementation", project(":core-domain"))
    add("implementation", project(":core-design"))
    add("implementation", project(":core-navigation"))
    add("implementation", project(":core-library"))
    add("implementation", project(":core-userdata"))
    add("implementation", project(":core-player"))
    add("implementation", "androidx.navigation3:navigation3-runtime:1.2.1")
    add("implementation", "com.jakewharton.timber:timber:5.0.1")
    add("ksp", "com.google.dagger:hilt-compiler:2.60.1")
}
