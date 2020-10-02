package info.kinterest.datastores.tests

import info.kinterest.DatastoreStarted
import info.kinterest.datastore.DatastoreConfig
import info.kinterest.datastores.DatastoreFactory
import io.kotest.assertions.asClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotlintest.provided.ProjectConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.kodein.di.DI
import org.kodein.di.instance
import org.kodein.di.on

@ExperimentalCoroutinesApi
class DatastoreFactorySpec : StringSpec({
    val kodein = DI {
        extend(kodeinTest)
    }

    "given a datasource" {
        ProjectConfig.datastores.forEach { datastore ->
            val fac : DatastoreFactory by kodein.instance<DatastoreFactory>()
            val name = "${datastore}dfspec"
            val cfg : DatastoreConfig by kodein.on(ProjectConfig).instance<DataStoreTypeAndName,DatastoreConfig>(arg = DataStoreTypeAndName(datastore, name))
            val listener = ChannelListener(fac.events.datastore.openSubscription())
            fac.create(cfg).shouldNotBeNull()
            runBlocking { listener.expect { it is DatastoreStarted && it.ds.name ==  name} }.asClue {
                it.shouldBeInstanceOf<DatastoreStarted>()
                it.ds.name.shouldBe(name)
            }
        }
    }
})