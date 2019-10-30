package info.kinterest.datastores.tests

import info.kinterest.datastore.Datastore
import info.kinterest.datastores.tests.jvm.PersonTransient
import io.kotlintest.forAll
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.provided.ProjectConfig
import io.kotlintest.shouldBe
import io.kotlintest.specs.FreeSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.kodein.di.Kodein
import org.kodein.di.generic.M
import org.kodein.di.generic.instance
import org.kodein.di.generic.on

@ExperimentalCoroutinesApi
class CrudSpec : FreeSpec({
    val kodein = Kodein {
        extend(kodeinTest)
    }

    forAll(ProjectConfig.datastores) { which ->
        "given a datastore $which" - {
            val ds: Datastore by kodein.on(ProjectConfig).instance(arg = M(which, "ds1"))
            runBlocking { ds.register(info.kinterest.datastores.tests.jvm.PersonJvm) }
            "inserting an entity" - {
                val pt = PersonTransient(mutableMapOf<String,Any?>("name" to "djuric", "first" to "sasa"))
                val pe = runBlocking {
                    ds.create(pt).fold({ throw it }) {
                        require(it.size == 1); it.size.shouldBe(1); it.first()
                    }
                }
                pe.shouldBeInstanceOf<Person>()
                require(pe is Person)
                "should work" - {
                    pe.name.shouldBe("djuric")
                }
                "updating should also work" - {
                    pe.name = "duric"
                    pe.name.shouldBe("duric")
                }
            }
            "given an entity" - {
                val datastore : Datastore by kodein.on(ProjectConfig).instance(arg = M(which, "dsdel1"))
                runBlocking { datastore.register(info.kinterest.datastores.tests.jvm.PersonJvm) }

                val pt = PersonTransient(mutableMapOf<String,Any?>("name" to "djuric", "first" to "sasa"))
                val pe = runBlocking { datastore.create(pt).fold({throw it}) { assert(it.size==1); it.first()} }
                require(pe is Person)
                "creating should work" - {
                    assert(pe.name == "djuric")
                    pe.name = "duric"
                    assert(pe.name == "duric")
                }

                val retrieved = runBlocking { datastore.retrieve(pe._meta, pe.id).fold({throw it}) {it} }.first()
                "retrieving should work" - {
                    retrieved.id.shouldBe(pe.id)
                    retrieved.shouldBeInstanceOf<Person>()
                    require(retrieved is Person)
                    retrieved.name.shouldBe(pe.name)
                }
                val ids = runBlocking { datastore.delete(pe).fold({throw it}) {it} }
                "deleting" - {
                    ids.shouldHaveSize(1)
                    ids.shouldContain(pe.id)
                }

                val retrievedAgain = runBlocking { datastore.retrieve(pe._meta, pe.id).fold({throw it}) {it} }
                "retrieving it again" - {
                    retrievedAgain.shouldHaveSize(0)
                }
            }
        }
    }

})