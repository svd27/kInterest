package info.kinterest.datastores.tests

import info.kinterest.DatastoreStarted
import info.kinterest.datastore.DatastoreConfig
import info.kinterest.datastores.DatastoreFactory
import io.kotlintest.Spec
import io.kotlintest.forAll
import io.kotlintest.matchers.asClue
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.matchers.types.shouldNotBeNull
import io.kotlintest.provided.ProjectConfig
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.kodein.di.Kodein
import org.kodein.di.generic.M
import org.kodein.di.generic.instance
import org.kodein.di.generic.on

@ExperimentalCoroutinesApi
class DatastoreFactorySpec : StringSpec({
    val kodein = Kodein {
        extend(kodeinTest)
    }

    "given a datasource" {
        forAll(ProjectConfig.datastores) { datastore ->
            val fac : DatastoreFactory by kodein.instance()
            val name = "${datastore}dfspec"
            val cfg : DatastoreConfig by kodein.on(ProjectConfig).instance(arg = M(datastore, name))
            val listener = ChannelListener(fac.events.datastore.openSubscription())
            fac.create(cfg).shouldNotBeNull()
            runBlocking { listener.expect { it is DatastoreStarted && it.ds.name ==  name} }.asClue {
                it.shouldBeInstanceOf<DatastoreStarted>()
                it.ds.name.shouldBe("ds1")
            }
        }
    }
})