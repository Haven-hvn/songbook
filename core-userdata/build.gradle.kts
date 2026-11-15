plugins {
    id("ipfsmedia.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "app.ipfsmedia.core.userdata"
}

dependencies {
    implementation(project(":core-wallet"))
    implementation(project(":core-security"))
    implementation(libs.kotlinx.coroutines)
    // Entities and DAO return types are part of this module's public surface.
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
}
