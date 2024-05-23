// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

plugins {
  id("java")
  id("org.jetbrains.intellij") version "1.17.3"
  id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "org.intellij.sdk"
version = "0.0.2"

repositories {
  mavenCentral()
}

dependencies {
  implementation("io.github.sashirestela:simple-openai:2.3.2")
//  implementation("org.openjfx:javafx-controls:17.0.1")
//  implementation("org.openjfx:javafx-swing:17.0.1")
//  implementation("org.openjfx:javafx-web:17.0.1")
//  implementation("org.openjfx:javafx-media:17.0.2")
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
}

// See https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
  type.set("PC")
  version.set("2024.1.1")
  plugins.add("python-ce")
}

tasks {
  buildSearchableOptions {
    enabled = false
  }

  patchPluginXml {
    version.set("${project.version}")
    sinceBuild.set("241")
    untilBuild.set("241.*")
  }
}

// JavaFX configuration
//javafx {
//  version = "17.0.1"
//  modules = listOf("javafx.controls", "javafx.swing", "javafx.web", "javafx.media")
//}