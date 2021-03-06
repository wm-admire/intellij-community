// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.importing.GradleBuildScriptBuilder.Companion.buildscript
import org.junit.Test

class GradleBuildScriptBuilderTest {
  @Test
  fun `test empty build script`() {
    assertThat(buildscript(GradleVersion.current()) {})
      .isEqualTo("")
  }

  @Test
  fun `test build script with plugins block`() {
    assertThat(buildscript(GradleVersion.current()) {
      addImport("org.example.Class1")
      addImport("org.example.Class2")
      addPlugin("id 'plugin-id'")
      addRepository("repositoryCentral()")
      addDependency("dependency 'my-dependency-id'")
      addPrefix("")
      withPrefix { call("println", "'Hello, Prefix!'") }
      withPostfix { call("println", "'Hello, Postfix!'") }
    }).isEqualTo("""
      import org.example.Class1
      import org.example.Class2
      plugins {
          id 'plugin-id'
      }
      
      println 'Hello, Prefix!'
      repositories {
          repositoryCentral()
      }
      dependencies {
          dependency 'my-dependency-id'
      }
      println 'Hello, Postfix!'
    """.trimIndent())
  }

  @Test
  fun `test build script with buildscript block`() {
    assertThat(buildscript(GradleVersion.current()) {
      addBuildScriptPrefix("println 'Hello, Prefix!'")
      withBuildScriptRepository { call("repo", "file('build/repo')") }
      withBuildScriptDependency { call("classpath", "file('build/targets/org/classpath/archive.jar')") }
      addBuildScriptPostfix("println 'Hello, Postfix!'")
      applyPlugin("'gradle-build'")
      addImport("org.classpath.Build")
      withPrefix {
        block("Build.configureSuperGradleBuild") {
          call("makeBeautiful")
        }
      }
    }).isEqualTo("""
      import org.classpath.Build
      buildscript {
          println 'Hello, Prefix!'
          repositories {
              repo file('build/repo')
          }
          dependencies {
              classpath file('build/targets/org/classpath/archive.jar')
          }
          println 'Hello, Postfix!'
      }
      apply plugin: 'gradle-build'
      Build.configureSuperGradleBuild {
          makeBeautiful()
      }
    """.trimIndent())
  }

  @Test
  fun `test build script deduplication`() {
    assertThat(buildscript(GradleVersion.current()) {
      withJUnit()
      withGroovyPlugin()
      withGroovyPlugin()
    }).isEqualTo("""
      apply plugin: 'groovy'
      repositories {
          maven {
              url 'https://repo.labs.intellij.net/repo1'
          }
      }
      dependencies {
          testImplementation 'org.junit.jupiter:junit-jupiter-api:5.7.0'
          testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.7.0'
          implementation 'org.codehaus.groovy:groovy-all:3.0.5'
      }
      test {
          useJUnitPlatform()
      }
    """.trimIndent())
  }

  @Test
  fun `test compile-implementation dependency scope`() {
    assertThat(buildscript(GradleVersion.current()) {
      withJUnit()
      addImplementationDependency("my-dep")
      addRuntimeOnlyDependency("my-runtime-dep")
    }).isEqualTo("""
      repositories {
          maven {
              url 'https://repo.labs.intellij.net/repo1'
          }
      }
      dependencies {
          testImplementation 'org.junit.jupiter:junit-jupiter-api:5.7.0'
          testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.7.0'
          implementation my-dep
          runtimeOnly my-runtime-dep
      }
      test {
          useJUnitPlatform()
      }
    """.trimIndent())
    assertThat(buildscript(GradleVersion.version("3.0")) {
      withJUnit()
      addImplementationDependency("my-dep")
      addRuntimeOnlyDependency("my-runtime-dep")
    }).isEqualTo("""
      repositories {
          maven {
              url 'https://repo.labs.intellij.net/repo1'
          }
      }
      dependencies {
          testCompile 'junit:junit:4.12'
          compile my-dep
          runtime my-runtime-dep
      }
    """.trimIndent())
    assertThat(buildscript(GradleVersion.version("4.0")) {
      withJUnit()
      addImplementationDependency("my-dep")
      addRuntimeOnlyDependency("my-runtime-dep")
    }).isEqualTo("""
      repositories {
          maven {
              url 'https://repo.labs.intellij.net/repo1'
          }
      }
      dependencies {
          testImplementation 'junit:junit:4.12'
          implementation my-dep
          runtimeOnly my-runtime-dep
      }
    """.trimIndent())
  }
}