import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.register<JavaExec>("coverageShare") {
    group = "verification"
    description =
        "Educational residue walk. Print the proved share by splitting open classes through -Pbits (default 16). " +
            "Does not rewrite summary.txt."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("hackman.trevor.collatz.CoverageShareKt")
    workingDir = rootProject.projectDir
    val bits = (findProperty("bits") as String?) ?: "16"
    args("scan", bits)
}

tasks.register<JavaExec>("mBucketShares") {
    group = "verification"
    description =
        "Print proved-share rows for m = 0 through -Pm (default 40). " +
            "Counts active parity words; does not scan residue classes."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("hackman.trevor.collatz.CoverageShareKt")
    workingDir = rootProject.projectDir
    val throughM = (findProperty("m") as String?) ?: "40"
    args("m", throughM)
}

tasks.register<JavaExec>("extendCoverageSummary") {
    group = "verification"
    description =
        "Rewrite data/classes/summary.txt from the parity-word count. " +
            "-PheapMb=N stops when the counters would exceed a 1/3 of an N-megabyte heap. " +
            "The table stops at 2^1000 (default bits: 1000)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("hackman.trevor.collatz.CoverageShareKt")
    workingDir = rootProject.projectDir
    val heapMb = findProperty("heapMb") as String?
    val bits = findProperty("bits") as String?
    if (heapMb != null) {
        jvmArgs("-Xmx${heapMb}m")
        args("extend-summary", "heap", heapMb)
    } else {
        args("extend-summary", bits ?: "1000")
    }
}

tasks.register<JavaExec>("rewriteCoverageSummary") {
    group = "verification"
    description = "Rewrite data/classes/summary.txt bit lines plus the m(b)=floor(log_3(2^b)) section."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("hackman.trevor.collatz.CoverageShareKt")
    workingDir = rootProject.projectDir
    args("rewrite-summary")
}

tasks.register<JavaExec>("logCycles") {
    group = "verification"
    description = "Rewrite data/cycles/summary.txt through -Plengths (default 20). Does not rewrite the class files."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("hackman.trevor.collatz.ProofLogKt")
    workingDir = rootProject.projectDir
    val lengths = (findProperty("lengths") as String?) ?: "20"
    args("cycles", lengths)
}

tasks.register<JavaExec>("logProofs") {
    group = "verification"
    description = "Write cycle and class proofs under data/."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("hackman.trevor.collatz.ProofLogKt")
    workingDir = rootProject.projectDir
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
        )
    }
}
