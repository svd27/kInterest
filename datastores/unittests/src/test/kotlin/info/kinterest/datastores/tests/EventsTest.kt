package info.kinterest.datastores.tests

import info.kinterest.*
import info.kinterest.datastore.Datastore
import info.kinterest.datastore.EventManager
import info.kinterest.datastores.dataStoresKodein
import info.kinterest.datastores.tests.jvm.PersonTransient
import info.kinterest.entity.PropertyName
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import mu.KotlinLogging
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.generic.instance
import org.kodein.di.generic.on
import org.kodein.di.generic.with
import strikt.api.expectThat
import strikt.assertions.*

@DisplayName("Events")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventsTest : KodeinAware {
    override lateinit var kodein: Kodein
    private val log = KotlinLogging.logger {}

    @BeforeAll
    fun beforeAll() {
        log.info { "before all" }
        TestScope.getRegistry(this)
        kodein = initKodein("testMongo", "testHazelcast")
    }

    @AfterAll
    fun afterAll() {
        log.info { "after all" }
        TestScope.close(this)
    }

    fun initKodein(mongoDbName:String, hazelcastName:String) : Kodein = Kodein {
        import(dataStoresKodein)
        import(kodeinMongo)
        import(kodeinHazelcast)
        constant("mongoDbName") with mongoDbName
        constant("hazelcastDs") with hazelcastName
    }


    @ParameterizedTest
    @ValueSource(strings = ["mongoDb", "hazelcast"])
    fun deleteTest(which:String) : Unit = runBlocking {
        val ds : Datastore by kodein.on(this).instance(which)
        val evMgr : EventManager by kodein.instance()
        val channelListener = ChannelListener(evMgr.listener(info.kinterest.datastores.tests.jvm.PersonJvm))
        ds.register(info.kinterest.datastores.tests.jvm.PersonJvm)

        val pt = PersonTransient(null, mutableMapOf("name" to "djuric", "first" to "sasa"))
        val pe = ds.create(pt).fold({throw it}) { assert(it.size==1); it.first()}
        require(pe is Person)
        expectThat(pe.name).isEqualTo("djuric")
        pe.name = "duric"
        expectThat(pe.name).isEqualTo("duric")
        val retrieved = ds.retrieve(pe._meta, pe.id).fold({throw it}) {it} .first()
        expectThat(retrieved.id) {
            isEqualTo(pe.id)
        }
        val ids = ds.delete(pe).fold({throw it}) {it}
        log.debug { "deleted $ids" }
        expectThat(ids) {
            hasSize(1)
            contains(pe.id)
        }
        val retrievedAgain = ds.retrieve(pe._meta, pe.id).fold({throw it}) {it}
        expectThat(retrievedAgain) {
            isEmpty()
        }
        val evt = channelListener.expect { it is EntitiesDeleted<*> &&  it.entities.containsAll(ids) }
        expectThat(evt) {
            isA<EntitiesDeleted<*>>()
            expectThat((evt as EntitiesDeleted<*>).entities) {
                containsExactly(ids)
            }
        }
        channelListener.close()
    }

    @ParameterizedTest
    @ValueSource(strings = ["mongoDb", "hazelcast"])
    fun createTest(which:String) : Unit = runBlocking {
        val ds : Datastore by kodein.on(this).instance(which)
        val evMgr : EventManager by kodein.instance()
        val channelListener = ChannelListener(evMgr.listener(info.kinterest.datastores.tests.jvm.PersonJvm))
        ds.register(info.kinterest.datastores.tests.jvm.PersonJvm)

        val pt = listOf(PersonTransient(null, mutableMapOf("name" to "djuric", "first" to "sasa")), PersonTransient(null, mutableMapOf("name" to "duric", "first" to "karin")))

        val ps = ds.create(*pt.toTypedArray()).fold({throw it}) { it}

        expectThat(ps).hasSize(2)

        val ids = ps.map { it.id }
        val retrieved = ds.retrieve(ps.first()._meta, ids).fold({throw it}) {it}
        expectThat(retrieved) {
            hasSize(2)
        }
        val evt = channelListener.expect {
            log.trace { "check $it" }
            it is EntitiesCreated<*,*> && it.entities.map { it.id }.containsAll(retrieved.map { it.id })
        }
        expectThat(evt) {
            isA<EntitiesCreated<*,*>>()
            expectThat((evt as EntitiesCreated<*,*>).entities.map { it.id }) {
                containsExactly(ids)
            }
        }
        channelListener.close()
    }

    @ParameterizedTest
    @ValueSource(strings = ["mongoDb", "hazelcast"])
    fun updateTest(which:String) : Unit = runBlocking {
        val ds : Datastore by kodein.on(this).instance(which)
        val evMgr : EventManager by kodein.instance()
        val channelListener = ChannelListener(evMgr.listener(info.kinterest.datastores.tests.jvm.PersonJvm))
        ds.register(info.kinterest.datastores.tests.jvm.PersonJvm)

        val pt = listOf(PersonTransient(null, mutableMapOf("name" to "djuric", "first" to "sasa")), PersonTransient(null, mutableMapOf("name" to "duric", "first" to "karin")))

        val ps = ds.create(pt).fold({throw it}) { it}

        expectThat(ps).hasSize(2)

        val ids = ps.map { it.id }
        val retrieved = ds.retrieve(ps.first()._meta, ids).fold({throw it}) {it}
        expectThat(retrieved) {
            hasSize(2)
        }
        log.debug { "retrieved: $retrieved: ${retrieved.map { require(it is Person); it.first }}" }
        retrieved.filterIsInstance<Person>().first { it.first == "sasa" }.apply { first = "sascha" }
        val evt = channelListener.expect {
            log.trace { "check $it" }
            it is EntityUpdated<*,*> && it.updates.any { it.property == PropertyName("first") && it.old == "sasa" && it.new == "sascha" }
        }
        expectThat(evt) {
            isA<EntityUpdated<*,*>>()
            val updates = (evt as EntityUpdated<*, *>).updates
            expectThat(updates) {
                exactly(1) {
                    assertThat("", null) {
                        it.property == PropertyName("first") && it.old == "sasa" && it.new == "sascha"
                    }
                }
            }
        }
        channelListener.close()
    }
}