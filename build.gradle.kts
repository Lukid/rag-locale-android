// Build file di primo livello: dichiara i plugin condivisi dai sotto-progetti.
// Versioni allineate alla reference anti-vocale (toolchain già validata su questo stack).
plugins {
    id("com.android.application") version "8.10.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
    id("com.google.devtools.ksp") version "2.2.0-2.0.2" apply false
    id("com.google.dagger.hilt.android") version "2.56.1" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1" apply false
}
