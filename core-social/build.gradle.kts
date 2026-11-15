plugins {
    id("ipfsmedia.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "app.ipfsmedia.core.social"
}

dependencies {
    api(project(":core-library"))
    implementation(project(":core-domain"))
    implementation(project(":core-userdata"))
    implementation(project(":core-arkiv"))
    implementation(project(":core-collections"))
    implementation(project(":core-cache-mirror"))
    implementation(project(":core-haven-aol"))
    implementation(project(":core-crypto"))
    implementation(project(":core-wallet"))
    implementation(project(":core-security"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.timber)
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    // Real org.json on the JVM test classpath: android.jar stubs throw RuntimeException("Stub!").
    testImplementation(libs.json)
}
