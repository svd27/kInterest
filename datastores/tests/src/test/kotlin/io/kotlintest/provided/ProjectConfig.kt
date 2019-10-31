package io.kotlintest.provided

import info.kinterest.datastores.hazelcast.HazelcastConfig
import info.kinterest.datastores.mongo.MongodatastoreConfig
import info.kinterest.datastores.tests.ProjectScope
import info.kinterest.datastores.tests.SpecScope
import info.kinterest.datastores.tests.TestScope
import io.kotlintest.AbstractProjectConfig
import io.kotlintest.Spec
import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.extensions.TestListener
import mu.KotlinLogging

object ProjectConfig : AbstractProjectConfig() {
    val datastores : List<String> = listOf(MongodatastoreConfig.TYPE, HazelcastConfig.TYPE)
    private val log = KotlinLogging.logger { }

    override fun parallelism(): Int = 2

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

    override fun listeners(): List<TestListener> {
        return super.listeners()+object : TestListener {
            override fun beforeSpec(spec: Spec) {
                log.info { "opening Spec scope for $spec" }
                SpecScope.getRegistry(spec)
            }

            override fun afterSpec(spec: Spec) {
                log.info { "closing Spec scope for $spec" }
                SpecScope.getRegistry(spec).close()
            }

            override fun beforeTest(testCase: TestCase) {
                log.info { "opening Test scope for $testCase" }
                TestScope.getRegistry(testCase)
            }

            override fun afterTest(testCase: TestCase, result: TestResult) {
                log.info { "closing Test scope for $testCase" }
                TestScope.getRegistry(testCase).close()
            }
        }
    }
}