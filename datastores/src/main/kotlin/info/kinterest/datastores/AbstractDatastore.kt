package info.kinterest.datastores

import info.kinterest.DatastoreEvent
import info.kinterest.EntitiesEvent
import info.kinterest.datastore.Datastore
import info.kinterest.datastore.DatastoreConfig
import info.kinterest.datastore.EventManager
import info.kinterest.entity.KIEntityMeta
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.sync.Mutex
import mu.KLogger
import mu.KotlinLogging
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.singleton

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

interface IdGenerator<V:Any> {
    fun next() : V
}

abstract class AbstractDatastore @ExperimentalCoroutinesApi constructor(cfg:DatastoreConfig, val events : EventManager) : Datastore {
    override val name: String = cfg.name
    val idGenerators : MutableMap<KIEntityMeta, IdGenerator<*>> = mutableMapOf()

    @ExperimentalCoroutinesApi
    protected suspend fun ready() {
        events.dataStoreStarted(this)
    }
}