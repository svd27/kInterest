package info.kinterest.datastores

import info.kinterest.datastore.Datastore
import info.kinterest.datastore.IdGenerator
import info.kinterest.entity.KIEntityMeta
import info.kinterest.functional.Try
import info.kinterest.functional.getOrElse
import mu.KotlinLogging
import kotlin.reflect.full.companionObject

class DatastoreManager(val countersDs:Datastore) {
    val log = KotlinLogging.logger {  }
    var metas : Map<String, KIEntityMeta> = mapOf()
    var datastores : Map<String,List<Datastore>> = mapOf()
    var datastoreMeta : Map<String,List<KIEntityMeta>> = mapOf()
    var generators : Map<KIEntityMeta, IdGenerator<*>> = mapOf()

    fun register(entitiesConfig: EntitiesConfig) {
        entitiesConfig.entities.map {
            val meta = Try {
                val cn = (it.name.split(".").dropLast(1) + "jvm").joinToString(".") + (it.name.split(".").last() + "Jvm")
                Class.forName(cn).kotlin.companionObject as KIEntityMeta
            }.getOrElse { ex -> log.warn(ex) { "exception trying to load metadata for ${it.name}" }; null }
            meta?.let { m ->
                if(metas.containsKey(m.name)) log.warn { "${m.name} already registered overwriting old definition" }
                metas = metas + (m.name to m)
                it.livesin.forEach {
                    datastoreMeta = datastoreMeta + (it to datastoreMeta.getOrDefault(it, listOf()) + m)
                }
            }
        }
    }
}