import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    id("com.android.application")
}

/**
 * Release signing. The keystore and its passwords live OUTSIDE the repository:
 * `android/keystore.properties` is gitignored, and the template is
 * `android/keystore.properties.example`. Losing the keystore means this app can
 * never be updated again, so back it up.
 *
 * The debug build type keeps the standard debug key, which is fine for a test
 * install and useless for an update: two debug keys never match.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

/**
 * data/quotes.json is the single source of truth for the corpus. This task
 * copies it into the APK assets and fails the build when the corpus is empty,
 * when an entry misses a key, or when the corpus shrinks below the floor.
 */
val copyCorpus by tasks.registering {
    val corpus = rootProject.layout.projectDirectory.file("../data/quotes.json")
    val target = layout.projectDirectory.file("src/main/assets/quotes.json")

    inputs.file(corpus)
    outputs.file(target)

    doLast {
        val file = corpus.asFile
        check(file.isFile) { "corpus not found at ${file.path}" }
        val root = JsonSlurper().parseText(file.readText()) as Map<*, *>
        val quotes = root["quotes"] as? List<*>
            ?: error("corpus must hold a quotes list")
        check(quotes.size >= 40) { "corpus holds ${quotes.size} quotes, expected at least 40" }
        quotes.forEachIndexed { index, entry ->
            val quote = entry as? Map<*, *> ?: error("quotes[$index] is not an object")
            listOf("author", "work", "text", "source").forEach { key ->
                check(quote.containsKey(key)) { "quotes[$index] misses the key $key" }
            }
            val text = quote["text"] as? String ?: error("quotes[$index].text is not a string")
            check(text.length in 40..400) { "quotes[$index].text has ${text.length} characters" }
            val source = quote["source"] as? String
                ?: error("quotes[$index].source is not a string")
            check(source.startsWith("https://en.wikiquote.org/")) {
                "quotes[$index].source is not a Wikiquote page: $source"
            }
        }
        target.asFile.parentFile.mkdirs()
        target.asFile.writeText(file.readText())
        logger.lifecycle("corpus: ${quotes.size} quotes copied into assets")
    }
}

android {
    namespace = "dev.cernoh.quotes"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.cernoh.quotes"
        minSdk = 26
        targetSdk = 36
        // A release sets these from the command line:
        //   gradle assembleRelease -PversionCode=5 -PversionName=0.5.0
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 4
        versionName = (project.findProperty("versionName") as String?) ?: "0.4.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(keystoreProperties.getProperty("storeFile"))
                    storePassword = keystoreProperties.getProperty("storePassword")
                    keyAlias = keystoreProperties.getProperty("keyAlias")
                    keyPassword = keystoreProperties.getProperty("keyPassword")
                    // v1 and v2 cover Android 8 and later; v3 adds key rotation,
                    // which this app does not use.
                    enableV1Signing = true
                    enableV2Signing = true
                    enableV3Signing = true
                }
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // Font files must not be compressed away from the resource table.
        noCompress += listOf("otf", "ttf")
    }
}

tasks.named("preBuild") {
    dependsOn(copyCorpus)
}

dependencies {
    // Shizuku runs a command as the shell or root user, so an update can install
    // without the system installer prompt. Version 12.2.0 is deliberate: the
    // public Shizuku.newProcess arrived in 12 and became private in 13, and the
    // streamed `pm install` needs it.
    implementation("dev.rikka.shizuku:api:12.2.0")
    implementation("dev.rikka.shizuku:provider:12.2.0")

    // The tests need JUnit, and a real org.json because the android.jar copy is
    // a stub that throws on the JVM.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
