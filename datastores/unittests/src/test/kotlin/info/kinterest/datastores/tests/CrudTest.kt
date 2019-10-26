package info.kinterest.datastores.tests

import info.kinterest.datastore.Datastore
import info.kinterest.datastores.dataStoresKodein
import info.kinterest.datastores.tests.jvm.PersonTransient
import kotlinx.coroutines.runBlocking
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
import strikt.assertions.contains
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo

@DisplayName("CRUD")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrudTest : KodeinAware {
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
    fun insertTest(which:String) {
        val ds : Datastore by kodein.on(this).instance(which)
        runBlocking { ds.register(info.kinterest.datastores.tests.jvm.PersonJvm) }

        val pt = PersonTransient(null, mutableMapOf("name" to "djuric", "first" to "sasa"))
        val pe = runBlocking { ds.create(pt).fold({throw it}) { assert(it.size==1); it.first()} }
        require(pe is Person)
        expectThat(pe.name).isEqualTo("djuric")
        pe.name = "duric"
        expectThat(pe.name).isEqualTo("duric")
    }

    @ParameterizedTest
    @ValueSource(strings = ["mongoDb", "hazelcast"])
    fun deleteTest(which:String) {
        val ds : Datastore by kodein.on(this).instance(which)
        runBlocking { ds.register(info.kinterest.datastores.tests.jvm.PersonJvm) }

        val pt = PersonTransient(null, mutableMapOf("name" to "djuric", "first" to "sasa"))
        val pe = runBlocking { ds.create(pt).fold({throw it}) { assert(it.size==1); it.first()} }
        require(pe is Person)
        assert(pe.name == "djuric")
        pe.name = "duric"
        assert(pe.name == "duric")
        val retrieved = runBlocking { ds.retrieve(pe._meta, pe.id).fold({throw it}) {it} }.first()
        expectThat(retrieved.id) {
            isEqualTo(pe.id)
        }
        val ids = runBlocking { ds.delete(pe).fold({throw it}) {it} }
        log.debug { "deleted $ids" }
        expectThat(ids) {
            hasSize(1)
            contains(pe.id)
        }
        val retrievedAgain = runBlocking { ds.retrieve(pe._meta, pe.id).fold({throw it}) {it} }
        expectThat(retrievedAgain) {
            isEmpty()
        }

    }
}