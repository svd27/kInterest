package info.kinterest.datastores.tests

import info.kinterest.datastore.Datastore
import info.kinterest.datastores.dataStoresKodein
import info.kinterest.datastores.hazelcast.HazelcastConfig
import info.kinterest.datastores.mongo.MongodatastoreConfig
import info.kinterest.datastores.tests.jvm.PersonTransient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.generic.*
import org.kodein.di.newInstance
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import java.util.stream.Stream

@DisplayName("CRUD")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrudTest : KodeinAware {
    override lateinit var kodein: Kodein
    private val log = KotlinLogging.logger {}

    @BeforeAll
    fun beforeAll() {
        log.info { "before all" }

        TestScope.getRegistry(this)
        kodein = initKodein()
    }

    @AfterAll
    fun afterAll() {
        log.info { "after all" }
        TestScope.close(this)
    }

    @ExperimentalCoroutinesApi
    fun initKodein() : Kodein = Kodein {
        import(kodeinTest)
        import(dataStoresKodein)
    }

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun types() = Stream.of(
                //MongodatastoreConfig.TYPE,
                HazelcastConfig.TYPE)
    }

    @DisplayName("insert")
    @ParameterizedTest(name = "Datastore: {0}")
    @MethodSource("types")
    fun insertTest(which:String) {
        val ds : Datastore by kodein.on(this).newInstance<Datastore> {instance(arg = M(which, "ds$which")) }
        runBlocking { ds.register(info.kinterest.datastores.tests.jvm.PersonJvm) }

        val pt = PersonTransient(null, mutableMapOf("name" to "djuric", "first" to "sasa"))
        val pe = runBlocking { ds.create(pt).fold({throw it}) { assert(it.size==1); it.first()} }
        require(pe is Person)
        expectThat(pe.name).isEqualTo("djuric")
        pe.name = "duric"
        expectThat(pe.name).isEqualTo("duric")
    }

    @ParameterizedTest
    @MethodSource("types")
    fun deleteTest(which:String) {
        val ds : Datastore by kodein.newInstance<Datastore> {instance(arg = M(which, "ds$which")) }
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