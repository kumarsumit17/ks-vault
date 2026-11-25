import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

// KS Vault, the phone app. Fully offline: see specs/constraints.md before adding anything here.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ---- Versioning (never hardcoded) ----------------------------------------------------------------
// -PversionName / -PversionCode come from the release workflow (from the tag). A build from source
// without them is version 0.0.0.
val appVersionName: String = providers.gradleProperty("versionName").orNull ?: "0.0.0"
val appVersionCode: Int = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 1

// ---- Signing: from SIGNING_* (CI) or an untracked keystore.properties (local) ----------------------
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

// Blank counts as missing: GitHub Actions passes an unset secret as an empty string.
fun signingValue(env: String, key: String): String? =
    providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.ks.vault"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ks.vault"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Phones (arm64, 32-bit ARM) and x86_64 emulators and Chromebooks. 32-bit x86 is left out:
        // only very old emulators use it (specs/plan-per-platform.md, decision 2).
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    val storeFilePath = signingValue("SIGNING_STORE_FILE", "storeFile")
    if (storeFilePath != null) {
        signingConfigs.create("release") {
            storeFile = file(storeFilePath)
            storePassword = signingValue("SIGNING_STORE_PASSWORD", "storePassword")
            keyAlias = signingValue("SIGNING_KEY_ALIAS", "keyAlias")
            keyPassword = signingValue("SIGNING_KEY_PASSWORD", "keyPassword")
        }
    }

    buildTypes {
        release {
            // R8 also removes every android.util.Log call (proguard-rules.pro).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources { localeFilters += listOf("en", "hi") }

    packaging {
        resources.excludes += listOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }
}

ksp {
    arg("room.schemaLocation", file("schemas").path)
}

dependencies {
    implementation(libs.ks.utility)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// ---- Language files (ks-utility) ---------------------------------------------------------------------
// The English and Hindi text lives in src/main/lang; the tool turns it (with the shared catalogue)
// into values/lang_strings.xml and values-hi/lang_strings.xml, which are git-ignored.
val langTool = rootProject.file("ks-utility/typescript/tools/lang.mjs")
val langDir = file("src/main/lang")
val checkLanguageFiles by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks the English and Hindi language files."
    commandLine("node", langTool, "check", langDir)
}
val languageResources by tasks.registering(Exec::class) {
    description = "Turns the language files into Android string resources."
    inputs.dir(langDir)
    inputs.dir(rootProject.file("ks-utility/lang"))
    outputs.files(file("src/main/res/values/lang_strings.xml"), file("src/main/res/values-hi/lang_strings.xml"))
    commandLine("node", langTool, "android", langDir, "--out", file("src/main/res"))
}
tasks.named("check") { dependsOn(checkLanguageFiles) }
tasks.named("preBuild") { dependsOn(languageResources) }

// ---- No network, ever ------------------------------------------------------------------------------
// Fails the build when the merged manifest of any variant asks for network access, so no library can
// add it unnoticed (specs/constraints.md).
abstract class CheckNoNetworkPermission : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val forbidden = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
        )
        val text = manifest.get().asFile.readText()
        val found = forbidden.filter { text.contains("\"$it\"") }
        if (found.isNotEmpty()) {
            throw GradleException("KS Vault must have no network access, but the merged manifest asks for: ${found.joinToString()}")
        }
        report.get().asFile.writeText("No network permission.\n")
    }
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val task = tasks.register<CheckNoNetworkPermission>("check${name}NoNetwork") {
            group = "verification"
            description = "Fails if the ${variant.name} merged manifest asks for network access."
            manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            report.set(layout.buildDirectory.file("reports/no-network/${variant.name}.txt"))
        }
        tasks.matching { it.name == "assemble$name" || it.name == "bundle$name" || it.name == "check" }
            .configureEach { dependsOn(task) }
    }
}
