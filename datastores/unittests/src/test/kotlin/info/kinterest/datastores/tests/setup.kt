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
import info.kinterest.datastore.Datastore
import info.kinterest.datastores.hazelcast.HazelcastConfig
import info.kinterest.datastores.hazelcast.HazelcastDatastore
import info.kinterest.datastores.mongo.MongoConfig
import info.kinterest.datastores.mongo.MongoDatastore
import info.kinterest.datastores.tests.containers.HazelcastClusterContainer
import info.kinterest.datastores.tests.containers.MongoClusterContainer
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

val kodeinMongo = Kodein.Module(name = "mongo") {
    constant("mongoIp") with "localhost"
    constant("mongoPort") with 27027
    bind<MongodExecutable>() with scoped(TestScope).singleton {
        val ip = instance<String>("mongoIp")
        val port: Int = instance("mongoPort")

        val cfg = MongodConfigBuilder().version(Version.Main.PRODUCTION).net(Net(ip, port, Network.localhostIsIPv6())).build()
        MongodStarter.getDefaultInstance().prepare(cfg)
    }
    bind<MongoClusterContainer>() with scoped(TestScope).singleton {
        MongoClusterContainer()
    }

    bind<KodeinCloseable<Pair<MongodExecutable, MongodProcess>>>() with scoped(TestScope).singleton {
        val exe: MongodExecutable = instance()
        object : KodeinCloseable<Pair<MongodExecutable, MongodProcess>> {
            val log = KotlinLogging.logger { }
            override val content = exe to exe.start()

            override fun close() {
                log.info { "close mongo process" }
                content.second.stop()
                log.info { "close mongo exe" }
                content.first.stop()
            }
        }
    }

    bind<MongodProcess>() with scoped(TestScope).singleton { instance<KodeinCloseable<Pair<MongodExecutable, MongodProcess>>>().content.second }

    bind<Datastore>("mongoDb") with provider {
        val dbName: String = instance("mongoDbName")
        //val process: MongodProcess = instance()
        val mongoClusterContainer : MongoClusterContainer = instance()

        val cfg = MongoConfig(dbName, mongoClusterContainer.mongoIp, mongoClusterContainer.mongoPort)
        MongoDatastore(cfg, instance())
    }
}

class HazelcastCloseable(vararg val instances: HazelcastInstance) : KodeinCloseable<List<HazelcastInstance>> {
    override val content: List<HazelcastInstance>
        get() = instances.asList()

    override fun invoke(): List<HazelcastInstance> = content

    override fun close() {
        content.firstOrNull()?.cluster?.shutdown()
    }
}

val kodeinHazelcast = Kodein.Module("hazelcast") {
    bind<HazelcastCloseable>() with scoped(TestScope).singleton {
        val cfg = Config()
        cfg.cpSubsystemConfig.cpMemberCount = 3
        HazelcastCloseable(
                Hazelcast.newHazelcastInstance(cfg),
                Hazelcast.newHazelcastInstance(cfg),
                Hazelcast.newHazelcastInstance(cfg)
        )
    }

    bind<HazelcastClusterContainer>() with scoped(TestScope).singleton {
        HazelcastClusterContainer()
    }

    bind<HazelcastInstance>() with scoped(TestScope).singleton {
        val hz : HazelcastCloseable = instance()
        hz.instances.first()
    }

    bind<Datastore>("hazelcast") with provider {
        //val hc = instance<HazelcastInstance>()
        val hazelcastClusterContainer : HazelcastClusterContainer = instance()
        val cfg = HazelcastConfig(instance("hazelcastDs"), mapOf())
        HazelcastDatastore(cfg, instance())
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