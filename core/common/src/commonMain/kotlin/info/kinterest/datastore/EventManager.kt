package info.kinterest.datastore

import info.kinterest.*
import info.kinterest.entity.KIEntity
import info.kinterest.entity.KIEntityMeta
import info.kinterest.entity.PropertyMeta
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KLogger
import mu.KotlinLogging

@ExperimentalCoroutinesApi
interface EventManager {
    val log : KLogger
      get() = KotlinLogging.logger {  }
    val datastore: BroadcastChannel<DatastoreEvent>
    var entityChannels: Map<KIEntityMeta, BroadcastChannel<EntitiesEvent>>
    val mutex: Mutex
    val scope get() = GlobalScope

    suspend fun dataStoreStarted(ds: Datastore) {
        datastore.send(DatastoreStarted(ds))
    }

    suspend fun listener(meta: KIEntityMeta) : ReceiveChannel<EntitiesEvent> = getEntityBroadcast(meta).apply { log.trace { "listener for $meta with base ${meta.baseMeta} resulted in $this" } }.openSubscription()

    private suspend fun getEntityBroadcast(meta: KIEntityMeta): BroadcastChannel<EntitiesEvent> = run {
        entityChannels.getOrElse(meta.baseMeta) {
            mutex.withLock {
                entityChannels.getOrElse(meta.baseMeta) {
                    val broadcastChannel = BroadcastChannel<EntitiesEvent>(100)
                    log.info { "created $broadcastChannel for $meta with base ${meta.baseMeta}" }
                    entityChannels = entityChannels + (meta.baseMeta to broadcastChannel)
                    broadcastChannel
                }
            }
        }
    }

    fun <ID : Any, E : KIEntity<ID>> entitiesCreated(entities: Iterable<E>) = run {
        val first = entities.firstOrNull()
        if (first != null) {
            val meta = first._meta

            scope.launch {
                log.info { "sending create $meta $entities" }
                getEntityBroadcast(meta).send(EntitiesCreated(meta, entities.toList()))
            }
        }
    }

    suspend fun <ID : Any> entitiesDeleted(meta: KIEntityMeta, entities: Set<ID>) = run {
        getEntityBroadcast(meta).send(EntitiesDeleted(meta, entities))
    }

    suspend fun <ID : Any, E : KIEntity<ID>> entityUpdated(e: E, updates: List<Pair<PropertyMeta, Pair<Any?, Any?>>>) {
        if(updates.isEmpty()) return
        getEntityBroadcast(e._meta).send(EntityUpdated<ID, E>(e._meta, e, updates.map { (prop, oldNew) ->
            val (old, new) = oldNew
            UpdateEvent(prop, old, new)
        })
        )
    }

    suspend fun listenDataStoreEvents(cb: (DatastoreEvent) -> Unit) {
        for (e in datastore.openSubscription())
            cb(e)
    }

    suspend fun listenEntityEvents(meta: KIEntityMeta, cb: (EntitiesEvent) -> Unit) {
        for (e in getEntityBroadcast(meta).openSubscription())
            cb(e)
    }
}