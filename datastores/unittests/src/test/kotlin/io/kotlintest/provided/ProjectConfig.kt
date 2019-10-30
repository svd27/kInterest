package io.kotlintest.provided

import info.kinterest.datastores.tests.ProjectScope
import io.kotlintest.AbstractProjectConfig
import io.kotlintest.Project
import io.kotlintest.Spec
import io.kotlintest.extensions.TestListener

object ProjectConfig : AbstractProjectConfig() {
    override fun afterAll() {
        ProjectScope.getRegistry(this)
    }

    override fun beforeAll() {
        ProjectScope.getRegistry(this).close()
    }

    override fun listeners(): List<TestListener> {
        return super.listeners()+object : TestListener {
            override fun beforeSpec(spec: Spec) {

            }
        }
    }

    init {

    }
}