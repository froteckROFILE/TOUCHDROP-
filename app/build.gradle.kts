plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "app.touchdrop"
    compileSdk = 35
    defaultConfig { applicationId = "app.touchdrop"; minSdk = 29; targetSdk = 35; versionCode = 9; versionName = "0.9" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    testImplementation("junit:junit:4.13.2")
}
