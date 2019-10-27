package info.kinterest.datastores.tests

import com.hazelcast.config.Config
import com.hazelcast.core.Hazelcast
import com.hazelcast.core.HazelcastInstance
import de.flapdoodle.embed.mongo.MongodExecutable
import de.flapdoodle.embed.mongo.MongodProcess
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.runtime.Network
import info.kinterest.DONTDOTHIS
import info.kinterest.datastore.Datastore
import info.kinterest.datastores.hazelcast.HazelcastConfig
import info.kinterest.datastores.hazelcast.HazelcastDatastore
import info.kinterest.datastores.mongo.MongodatastoreConfig
import info.kinterest.datastores.mongo.MongoDatastore
import info.kinterest.datastores.tests.containers.HazelcastClusterContainer
import info.kinterest.datastores.tests.containers.MongoClusterContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import mu.KotlinLogging
import org.kodein.di.Kodein
import org.kodein.di.bindings.Scope
import org.kodein.di.bindings.ScopeCloseable
import org.kodein.di.bindings.ScopeRegistry
import org.kodein.di.bindings.StandardScopeRegistry
import org.kodein.di.generic.*

interface KodeinCloseable<T> : ScopeCloseable {
    val content: T

    operator fun invoke(): T = content
}

@ExperimentalCoroutinesApi
val kodeinMongo = Kodein.Module(name = "mongo") {
    bind<MongoClusterContainer>() with scoped(TestScope).singleton {
        MongoClusterContainer()
    }

    bind<MongodatastoreConfig>() with multiton {name : String ->
        val cluster : MongoClusterContainer = instance()
        MongodatastoreConfig(name, cluster.mongoIp, cluster.mongoPort)
    }

    bind<MongoDatastore>() with multiton { name : String ->
        MongoDatastore(factory<String,MongodatastoreConfig>()(name), instance())
    }
}

@ExperimentalCoroutinesApi
val kodeinHazelcast = Kodein.Module("hazelcast") {
    bind<HazelcastClusterContainer>() with scoped(TestScope).singleton {
        HazelcastClusterContainer()
    }


    bind<HazelcastConfig>() with multiton { name : String ->
        val cluster : HazelcastClusterContainer = instance()
        HazelcastConfig(name, cluster.addresses)
    }

    bind<HazelcastDatastore>() with multiton { name : String ->
        HazelcastDatastore(factory<String,HazelcastConfig>()(name), instance())
    }
}

@ExperimentalCoroutinesApi
val kodeinTest : Kodein.Module = Kodein.Module("test") {
    import(kodeinMongo)
    import(kodeinHazelcast)
    bind<Datastore>() with multiton { type: String, name: String ->
        when (type) {
            MongodatastoreConfig.TYPE -> factory<String,MongoDatastore>()(name)
            HazelcastConfig.TYPE -> factory<String,HazelcastDatastore>()(name)
            else -> DONTDOTHIS()
        }
    }
}

object TestScope : Scope<Any> {
    private val mapRegistry = HashMap<Any, ScopeRegistry>()
    private val log = KotlinLogging.logger { }

    override fun getRegistry(context: Any): ScopeRegistry = synchronized(mapRegistry) {
        log.info { "register $context" }
        mapRegistry[context] ?: run {
            val scopeRegistry = StandardScopeRegistry()
            mapRegistry[context] = scopeRegistry
            log.info { "register $context with $scopeRegistry" }
            scopeRegistry
        }
    }

    fun close(context: Any) {
        synchronized(mapRegistry) {
            val scopeRegistry = mapRegistry[context]
            if (scopeRegistry != null) {
                mapRegistry.remove(context)
                log.info { "close scope $context clearing $scopeRegistry" }
                scopeRegistry.clear()
            }
        }
    }
}