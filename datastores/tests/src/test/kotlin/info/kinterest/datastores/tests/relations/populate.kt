package info.kinterest.datastores.tests.relations

import info.kinterest.datastore.Datastore
import info.kinterest.datastores.tests.relations.jvm.PersonTransient
import info.kinterest.functional.getOrElse
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

object Populate {
    private val log = KotlinLogging.logger { }
    fun populate(ds: Datastore) {
        val persons = List(100) {
            PersonTransient(it + 1, "${'A' + (it % 22)}lice", "${'Z' - (it % 22)}ero", null)
        }
        runBlocking { ds.create(persons).getOrElse { throw it }.toList(mutableListOf()) }
    }
}