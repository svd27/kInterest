package info.kinterest.datastores.tests

import info.kinterest.EntitiesCreated
import info.kinterest.EntitiesDeleted
import info.kinterest.EntityUpdated
import info.kinterest.datastore.Datastore
import info.kinterest.datastore.EventManager
import info.kinterest.datastores.tests.jvm.PersonJvm
import info.kinterest.datastores.tests.jvm.PersonTransient
import io.kotest.assertions.asClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotlintest.provided.ProjectConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import mu.KotlinLogging
import org.kodein.di.DI
import org.kodein.di.instance
import org.kodein.di.on

@ExperimentalCoroutinesApi
class EventsSpec : FreeSpec({
    val log = KotlinLogging.logger { }
    val kodein = DI {
        extend(kodeinTest)
    }
    ProjectConfig.datastores.forEach { which ->
        val ds: Datastore by kodein.on(ProjectConfig).instance<DataStoreTypeAndName,Datastore>(arg = DataStoreTypeAndName(which, "evtsds1"))
        val evMgr: EventManager by kodein.instance<EventManager>()
        "given a datastore($which)" - {
            val channelListener = ChannelListener(evMgr.listener(PersonJvm))
            "operations" - {
                ds.register(PersonJvm)

                "creating some entities of type ${PersonJvm}" - {
                    val pt = listOf(PersonTransient(mutableMapOf<String,Any?>("name" to "djuric", "first" to "sasa")),
                            PersonTransient(mutableMapOf<String,Any?>("name" to "duric", "first" to "karin")))

                    val ps = ds.create(pt).fold({ throw it }) { it }.toList(mutableListOf())
                    ps.shouldHaveSize(2)

                    val ids = ps.map { it.id }
                    val retrieved = ds.retrieve(ps.first()._meta, ids).fold({ throw it }) { it }.toList(mutableListOf())
                    retrieved.shouldHaveSize(2)
                    val evt = channelListener.expect {
                        log.trace { "check $it" }
                        it is EntitiesCreated<*, *> && it.entities.map { it.id }.containsAll(retrieved.map { it.id })
                    }
                    evt.asClue {
                        it.shouldBeInstanceOf<EntitiesCreated<*, *>>()
                        require(it is EntitiesCreated<*, *>)
                        it.entities.forEach {
                            it.shouldBeInstanceOf<Person>()
                        }
                        it.entities.map { it.id }.shouldContainExactly(ids)
                    }
                }

                "given an enitity of type ${PersonJvm}" - {
                    val pt = PersonTransient(mutableMapOf<String,Any?>("name" to "djuric", "first" to "sasa"))

                    val pe = ds.create(pt).fold({ throw it }) { it }.first()
                    require(pe is Person)

                    val ids = ds.delete(pe).fold({ throw it }) { it }
                    "deleting $pe works" {
                        log.debug { "deleted $ids" }
                        ids.asClue {
                            it.shouldHaveSize(1)
                            it.shouldContain(pe.id)
                        }
                    }

                    val retrievedAgain = ds.retrieve(pe._meta, pe.id).fold({ throw it }) { it }.toList(mutableListOf())
                    "retrieving it again should not work" {
                        retrievedAgain.shouldBeEmpty()
                    }

                    channelListener.expect { it is EntitiesDeleted<*> && it.entities.containsAll(ids) }.asClue {
                        it.shouldBeInstanceOf<EntitiesDeleted<*>>()
                        require(it is EntitiesDeleted<*>)
                        it.entities.shouldContainExactly(ids)
                    }
                }

                "given some entities of type ${PersonJvm}" - {
                    val pt = listOf(
                            PersonTransient(mutableMapOf<String,Any?>("name" to "djuric", "first" to "sasa")),
                            PersonTransient(mutableMapOf<String,Any?>("name" to "duric", "first" to "karin")))

                    val ps = ds.create(pt).fold({ throw it }) { it }.toList(mutableListOf())

                    ps.shouldHaveSize(2)

                    val ids = ps.map { it.id }
                    val retrieved = ds.retrieve(ps.first()._meta, ids).fold({ throw it }) { it }.toList(mutableListOf())
                    retrieved.shouldHaveSize(2)
                    log.debug { "retrieved: $retrieved: ${retrieved.map { require(it is Person); it.first }}" }
                    retrieved.filterIsInstance<Person>().first { it.first == "sasa" }.apply { first = "sascha" }
                    val evt = channelListener.expect {
                        log.trace { "check $it" }
                        it is EntityUpdated<*, *> && it.updates.any { it.property.name == "first" && it.old == "sasa" && it.new == "sascha" }
                    }
                    evt.asClue {
                        it.shouldBeInstanceOf<EntityUpdated<*, *>>()
                        require(it is EntityUpdated<*, *>)
                        it.asClue {
                            it.updates.asClue {
                                it.shouldHaveSize(1)
                                it.first().asClue {
                                    it.property.shouldBe(PersonJvm.FIRST)
                                    it.old.shouldBe("sasa")
                                    it.new.shouldBe("sascha")
                                }
                            }
                        }
                    }
                }
            }
            channelListener.close()
        }
    }
})