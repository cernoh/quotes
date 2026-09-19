import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
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
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    // Framework APIs only in the app. The tests use JUnit for the size classes.
    testImplementation("junit:junit:4.13.2")
}
