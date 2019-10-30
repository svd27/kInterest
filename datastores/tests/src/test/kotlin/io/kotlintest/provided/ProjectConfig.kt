package io.kotlintest.provided

import info.kinterest.datastores.hazelcast.HazelcastConfig
import info.kinterest.datastores.mongo.MongodatastoreConfig
import info.kinterest.datastores.tests.ProjectScope
import info.kinterest.datastores.tests.SpecScope
import info.kinterest.datastores.tests.TestScope
import io.kotlintest.*
import io.kotlintest.extensions.TestListener
import io.kotlintest.matchers.reflection.shouldHaveReturnType
import mu.KotlinLogging
import org.kodein.di.Kodein
import kotlin.reflect.full.declaredFunctions

object ProjectConfig : AbstractProjectConfig() {
    val datastores : List<String> = listOf(MongodatastoreConfig.TYPE, HazelcastConfig.TYPE)
    private val log = KotlinLogging.logger { }

    override fun parallelism(): Int = 2

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