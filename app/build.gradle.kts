import java.io.File
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// One fixed key, committed. A debug key is not a secret: the point is only that every build,
// here and in CI, is signed with the same one, so a new APK installs over the last instead of
// forcing an uninstall that wipes the favorites, the avatar and the accessibility setting.
// Anyone can sign an APK with it, so it is worth nothing as proof of origin. Point
// keystore.properties, or the ORBIT_STORE_FILE and friends environment variables, at a real
// key to override it.
val keyProperties = Properties().apply {
    val file = System.getenv("ORBIT_KEYSTORE_PROPERTIES")?.let(::File)
        ?: rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use(::load)
}

/** Reads one signing value, preferring keystore.properties over ORBIT_SCREAMING_SNAKE. */
fun signingValue(name: String): String? {
    val variable = "ORBIT_" + name.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()
    return (keyProperties.getProperty(name) ?: System.getenv(variable))?.takeIf(String::isNotBlank)
}

val committedKey = file("debug.jks")
val signingKeystore: File = signingValue("storeFile")?.let(::File)?.takeIf(File::isFile)
    ?: committedKey
val usingCommittedKey = signingKeystore == committedKey

kotlin {
    compilerOptions {
        // The DSL that replaced kotlinOptions, which Kotlin 2.4 removed outright. Set here
        // rather than inside android { }, which is where the old one lived.
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "app.orbit.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.orbit.launcher"
        minSdk = 29
        targetSdk = 35
        // CI passes its run number, so each build installs over the one before it. A build here
        // keeps 1 unless ORBIT_VERSION_CODE says otherwise.
        versionCode = (System.getenv("ORBIT_VERSION_CODE") ?: "1").toInt()
        versionName = "0.1.0"
    }

    signingConfigs {
        create("orbit") {
            storeFile = signingKeystore
            storePassword = if (usingCommittedKey) "android" else signingValue("storePassword")
            keyAlias = if (usingCommittedKey) "androiddebugkey" else signingValue("keyAlias")
            keyPassword = if (usingCommittedKey) "android" else signingValue("keyPassword")
        }
    }

    buildTypes {
        // One key for both, so a debug build installs over a release one and back again without
        // the uninstall that would wipe the favorites, the avatar and the accessibility setting.
        // A debug build stays debuggable, which is what makes run-as work for diagnostics.
        val signing = signingConfigs.getByName("orbit")

        debug {
            signingConfig = signing
        }

        release {
            signingConfig = signing
            // Compose on a TV chip is noticeably smoother shrunk, which shows in the focus
            // pop-out and the scrolling labels.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Warnings that were already there when lint was switched on. New ones fail the build.
        baseline = file("lint-baseline.xml")
        // Missing package visibility hides the owner's apps on Android 11 and later, and the
        // favorites prune would then delete them. Never let that through again.
        error += "QueryPermissionsNeeded"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.maxHeapSize = "2g"
                it.testLogging { showStandardStreams = true }
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.39.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.39.0")
}
