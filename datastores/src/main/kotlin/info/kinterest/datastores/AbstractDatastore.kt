package info.kinterest.datastores

import info.kinterest.DatastoreEvent
import info.kinterest.EntitiesEvent
import info.kinterest.datastore.Datastore
import info.kinterest.datastore.DatastoreConfig
import info.kinterest.datastore.EventManager
import info.kinterest.datastore.IdGenerator
import info.kinterest.entity.KIEntityMeta
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.sync.Mutex
import mu.KLogger
import mu.KotlinLogging
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.singleton
import java.util.*

@ExperimentalCoroutinesApi
val kodeinDatastores = Kodein.Module("dataStores") {
    bind<EventManager>() with singleton {
        object : EventManager {
            override val log: KLogger = KotlinLogging.logger(EventManager::class.qualifiedName!!)
            override val datastore: BroadcastChannel<DatastoreEvent> = BroadcastChannel(10)
            override var entityChannels: Map<KIEntityMeta, BroadcastChannel<EntitiesEvent>> = mapOf()
            override val mutex: Mutex = Mutex()
        }
    }
    bind<DatastoreFactory>() with singleton { DatastoreFactory(instance()) }
}


abstract class AbstractDatastore @ExperimentalCoroutinesApi constructor(cfg:DatastoreConfig, val events : EventManager) : Datastore {
    override val name: String = cfg.name
    override val instanceId: Any = UUID.randomUUID()

    val idGenerators : MutableMap<KIEntityMeta, IdGenerator<*>> = mutableMapOf()

    object UUIDGenerator : IdGenerator<UUID> {
        override fun next(): UUID = UUID.randomUUID()
    }

    @ExperimentalCoroutinesApi
    protected suspend fun ready() {
        events.dataStoreStarted(this)
    }
}