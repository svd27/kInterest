package info.kinterest.datastores.tests

import com.hazelcast.core.HazelcastInstance
import de.flapdoodle.embed.mongo.MongodProcess
import info.kinterest.DatastoreEvent
import info.kinterest.datastore.DatastoreConfig
import info.kinterest.datastore.EventManager
import info.kinterest.datastores.DatastoreFactory
import info.kinterest.datastores.dataStoresKodein
import info.kinterest.datastores.hazelcast.HazelcastConfig
import info.kinterest.datastores.mongo.MongodatastoreConfig
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.junit.jupiter.api.*
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.generic.*
import strikt.api.expectThat
import strikt.assertions.isGreaterThan

@DisplayName("DatastoreFactory")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatastoreFactoryTest : KodeinAware {
    override lateinit var kodein : Kodein
    val log = KotlinLogging.logger {  }

    @BeforeAll
    fun setUpClass() {
        log.info { "before all" }
        TestScope.getRegistry(this)
        kodein = Kodein {
            import(dataStoresKodein)
            import(kodeinMongo)
            import(kodeinHazelcast)
            bind<DatastoreConfig>("ds1") with scoped(TestScope).singleton {
                val mongodProcess = instance<MongodProcess>()
                MongodatastoreConfig("ds1", "localhost", 27027)
            }

            bind<DatastoreConfig>("ds2") with scoped(TestScope).singleton {
                instance<HazelcastInstance>()
                HazelcastConfig("ds2", mapOf())
            }
        }
    }

    @AfterAll
    fun afterAll() {
        TestScope.close(this)
    }


    @Test
    fun testLoaded() {
        val fac by kodein.on(this).instance<DatastoreFactory>()
        expectThat(fac) {
            expectThat(fac.configs.size) {
                isGreaterThan(0)
            }
        }
    }

    @Test
    fun testCreate() {
        val eventManager : EventManager by kodein.on(this).instance()
        val channelListener = ChannelListener<DatastoreEvent>(eventManager.datastore.openSubscription())
        val fac by kodein.on(this).instance<DatastoreFactory>()
        val cfg : DatastoreConfig by kodein.on(this).instance("ds1")
        fac.create(cfg)
        val cfg2 : DatastoreConfig by kodein.on(this).instance("ds2")
        fac.create(cfg2)
        runBlocking {
            channelListener.expect { it.ds.name == cfg.name }
            channelListener.expect { it.ds.name == cfg2.name }
        }
    }
}