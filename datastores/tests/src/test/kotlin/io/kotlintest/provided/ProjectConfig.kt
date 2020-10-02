package io.kotlintest.provided

import info.kinterest.datastores.hazelcast.HazelcastConfig
import info.kinterest.datastores.mongo.MongodatastoreConfig
import info.kinterest.datastores.tests.ProjectScope
import info.kinterest.datastores.tests.SpecScope
import info.kinterest.datastores.tests.TestScope
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.listeners.Listener
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import mu.KotlinLogging
import kotlin.math.max

object ProjectConfig : AbstractProjectConfig() {
    val datastores : List<String> = listOf(HazelcastConfig.TYPE, MongodatastoreConfig.TYPE)
    private val log = KotlinLogging.logger { }

    override val parallelism: Int = max(Runtime.getRuntime().availableProcessors() / 2, 1)

    init {
        log.info { "TMPDIR: ${System.getProperty("java.io.tmpdir")}" }
        if(System.getProperty("java.io.tmpdir") == """C:\windows\""")
            System.setProperty("java.io.tmpdir", """build\tmp""" )
    }

    override fun afterAll() {
        log.info { "closing project scope" }
        ProjectScope.getRegistry(this).close()
    }

    override fun beforeAll() {
        log.info { "opening project scope" }
        ProjectScope.getRegistry(this)
    }

    override fun listeners(): List<Listener> {
        return super.listeners()+object : TestListener {
            override suspend fun beforeSpec(spec: Spec) {
                log.info { "opening Spec scope for $spec" }
                SpecScope.getRegistry(spec)
            }

            override suspend fun afterSpec(spec: Spec) {
                log.info { "closing Spec scope for $spec" }
                SpecScope.getRegistry(spec).close()
            }

            override suspend fun beforeTest(testCase: TestCase) {
                log.info { "opening Test scope for $testCase" }
                TestScope.getRegistry(testCase)
            }

            override suspend fun afterTest(testCase: TestCase, result: TestResult) {
                log.info { "closing Test scope for $testCase" }
                TestScope.getRegistry(testCase).close()
            }
        }
    }
}