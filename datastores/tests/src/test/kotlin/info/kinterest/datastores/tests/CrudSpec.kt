package info.kinterest.datastores.tests

import info.kinterest.datastore.Datastore
import info.kinterest.datastores.tests.jvm.PersonTransient
import info.kinterest.functional.getOrElse
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotlintest.provided.ProjectConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.kodein.di.DI
import org.kodein.di.instance
import org.kodein.di.on

@ExperimentalCoroutinesApi
class CrudSpec : FreeSpec({
    val kodein = DI {
        extend(kodeinTest)
    }

    ProjectConfig.datastores.forEach {
        which : String ->
        "given a datastore $which" - {
            val ds: Datastore by kodein.on(ProjectConfig).instance<DataStoreTypeAndName,Datastore>(arg = DataStoreTypeAndName(which, "ds1"))
            runBlocking { ds.register(info.kinterest.datastores.tests.jvm.PersonJvm) }
            "inserting an entity" - {
                val pt = PersonTransient(null, "sasa", "djuric", 4L)
                val pe = runBlocking {
                    val res = ds.create(pt).getOrElse { throw it }
                    val l = res.toList(mutableListOf())
                    require(l.size == 1)
                    l.size.shouldBe(1)
                    l.first()
                }

                pe.shouldBeInstanceOf<Person>()
                require(pe is Person)
                "should work" - {
                    pe.name.shouldBe("djuric")
                    pe.age.shouldBeNull()
                }
                "updating should also work" - {
                    pe.name = "duric"
                    pe.age = 51
                    pe.name.shouldBe("duric")
                    pe.age .shouldBe(51)
                    pe.age = null
                    pe.age.shouldBeNull()
                }
            }
            "given an entity" - {
                val datastore: Datastore by kodein.on(ProjectConfig).instance(arg = DataStoreTypeAndName(which, "dsdel1"))
                runBlocking { datastore.register(info.kinterest.datastores.tests.jvm.PersonJvm) }

                val pt = PersonTransient(mutableMapOf<String, Any?>("name" to "djuric", "first" to "sasa"))
                val pe = runBlocking {
                    val createTry = datastore.create(pt)
                    val res = createTry.getOrElse { throw it }
                    val l = res.toList(mutableListOf())
                    check(l.size == 1); l.first()
                }

                check(pe is Person)
                "creating should work" - {
                    assert(pe.name == "djuric")
                    pe.name = "duric"
                    assert(pe.name == "duric")
                }

                val retrieved = runBlocking { datastore.retrieve(pe._meta, pe.id).fold({ throw it }) { it } }.first()
                "retrieving should work" - {
                    retrieved.id.shouldBe(pe.id)
                    retrieved.shouldBeInstanceOf<Person>()
                    retrieved.name.shouldBe(pe.name)
                }
                val ids = runBlocking { datastore.delete(pe).fold({ throw it }) { it } }
                "deleting" - {
                    ids.shouldHaveSize(1)
                    ids.shouldContain(pe.id)
                }

                val retrievedAgain = runBlocking { datastore.retrieve(pe._meta, pe.id).fold({ throw it }) { it } }.toList(mutableListOf())
                "retrieving it again" - {
                    retrievedAgain.shouldHaveSize(0)
                }
            }
        }
    }

})