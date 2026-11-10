// The root project holds no sources; it only carries shared configuration and the
// plugin versions the modules resolve against.
plugins {
    `java-base`
    id("com.gradleup.shadow") version "9.0.2" apply false
}

// Settings shared by both modules. Java versions deliberately differ and are set
// per module: the proxy targets 21, while the Paper plugin has to run inside a
// server that may still be on 17.
subprojects {
    apply(plugin = "java")

    group = "dev.relay"
    version = "0.1.0-SNAPSHOT"

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:all,-serial,-processing")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

// Documentation uses a pinned copy of the Site toolchain rather than checking a
// second doclet into Relay. Only this opt-in task reads that checkout; normal
// builds and the proxy/Paper runtime targets stay unchanged.
val docsToolchain = providers.gradleProperty("pustkaDocsToolchain")
val docsJava = extensions.getByType<JavaToolchainService>()
val docsClasses = layout.buildDirectory.dir("pustka-doclet")
val docsOutput = layout.buildDirectory.file("pustka-docs/api.json")
val compilePustkaDoclet = tasks.register<JavaCompile>("compilePustkaDoclet") {
    group = "documentation"
    description = "Compiles the pinned Pustka Java 21 documentation adapter"
    source(docsToolchain.map { file("$it/tools/javadoc/PustkaDoclet.java") })
    classpath = files()
    destinationDirectory = docsClasses
    javaCompiler = docsJava.compilerFor { languageVersion = JavaLanguageVersion.of(21) }
    options.release = 21
    options.encoding = "UTF-8"
    doFirst {
        require(docsToolchain.isPresent) {
            "Pass -PpustkaDocsToolchain=/absolute/path/to/the/pinned/Site/checkout"
        }
    }
}

tasks.register<Exec>("generatePustkaDocs") {
    group = "documentation"
    description = "Extracts Relay's public/protected proxy API and Javadoc comments"
    dependsOn(compilePustkaDoclet)
    val proxy = project(":proxy")
    val mainSources = provider {
        proxy.extensions.getByType<JavaPluginExtension>().sourceSets.getByName("main")
    }
    val javadocTool = docsJava.javadocToolFor { languageVersion = JavaLanguageVersion.of(21) }
    inputs.files(mainSources.map { it.allJava }).withPropertyName("apiSources")
    inputs.files(mainSources.map { it.compileClasspath }).withPropertyName("compileClasspath")
    inputs.dir(docsClasses).withPropertyName("doclet")
    outputs.file(docsOutput)
    doFirst {
        val sources = mainSources.get()
        val output = docsOutput.get().asFile
        output.parentFile.mkdirs()
        // An argument file avoids Windows command-length limits. Every value is
        // a Javadoc argument, never shell text; project classes are not executed.
        fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val arguments = listOf(
            "-quiet", "-protected", "-encoding", "UTF-8",
            "-doclet", "sh.pustka.docs.PustkaDoclet",
            "-docletpath", docsClasses.get().asFile.absolutePath,
            "--source-root", rootDir.absolutePath,
            "--output", output.absolutePath,
            "-classpath", sources.compileClasspath.asPath,
        ) + sources.allJava.files.sortedBy { it.absolutePath }.map { it.absolutePath }
        val argumentFile = layout.buildDirectory.file("pustka-docs/javadoc.args").get().asFile
        argumentFile.writeText(arguments.joinToString("\n") { quoted(it) } + "\n", Charsets.UTF_8)
        commandLine(javadocTool.get().executablePath.asFile.absolutePath, "@${argumentFile.absolutePath}")
    }
}

// Documentation examples compile against the current project APIs, never copied
// stub. Their complete source is also compared with the published Markdown by
// the Site checker, so a guide cannot silently diverge from the checked example.
val checkPustkaPaperExamples = tasks.register<JavaCompile>("checkPustkaPaperExamples") {
    group = "documentation"
    description = "Compiles Paper integration recipes with the plugin's real classpath"
    dependsOn(":paper-plugin:classes")
    source(fileTree("docs/examples/paper") { include("**/*.java") })
    val paperSources = provider {
        project(":paper-plugin").extensions.getByType<JavaPluginExtension>().sourceSets.getByName("main")
    }
    classpath = files(paperSources.map { it.output }, paperSources.map { it.compileClasspath })
    destinationDirectory = layout.buildDirectory.dir("pustka-docs/paper-example-classes")
    javaCompiler = docsJava.compilerFor { languageVersion = JavaLanguageVersion.of(21) }
    options.release = 17
    options.encoding = "UTF-8"
}

tasks.register<JavaCompile>("checkPustkaExamples") {
    group = "documentation"
    description = "Compiles the Java examples included in the published guides"
    dependsOn(":proxy:classes", checkPustkaPaperExamples)
    source(fileTree("docs/examples/java") { include("**/*.java") })
    val proxySources = provider {
        project(":proxy").extensions.getByType<JavaPluginExtension>().sourceSets.getByName("main")
    }
    classpath = files(proxySources.map { it.output }, proxySources.map { it.compileClasspath })
    destinationDirectory = layout.buildDirectory.dir("pustka-docs/example-classes")
    javaCompiler = docsJava.compilerFor { languageVersion = JavaLanguageVersion.of(21) }
    options.release = 21
    options.encoding = "UTF-8"
}
