package info.kinterest.datastores.tests

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import info.kinterest.DONTDOTHIS
import info.kinterest.datastore.Datastore
import info.kinterest.datastores.kodeinDatastores
import info.kinterest.datastores.hazelcast.HazelcastConfig
import info.kinterest.datastores.hazelcast.HazelcastDatastore
import info.kinterest.datastores.mongo.MongoDatastore
import info.kinterest.datastores.mongo.MongodatastoreConfig
import info.kinterest.docker.hazelcast.HazelcastCluster
import kotlinx.coroutines.ExperimentalCoroutinesApi
import mu.KotlinLogging
import org.kodein.di.Kodein
import org.kodein.di.bindings.Scope
import org.kodein.di.bindings.ScopeCloseable
import org.kodein.di.bindings.ScopeRegistry
import org.kodein.di.bindings.StandardScopeRegistry
import org.kodein.di.generic.*
import java.time.Duration
import java.util.stream.Stream

interface KodeinCloseable<T> : ScopeCloseable {
    val content: T

    operator fun invoke(): T = content
}

@ExperimentalCoroutinesApi
val kodeinMongo = Kodein.Module(name = "mongo") {
    bind<MongoDatastore>() with multiton { name : String ->
        MongoDatastore(factory<String,MongodatastoreConfig>()(name), instance())
    }
}


@ExperimentalCoroutinesApi
val kodeinHazelcast = Kodein.Module("hazelcast") {
    bind<DockerClient>() with singleton {
        val cmds: DockerCmdExecFactory = NettyDockerCmdExecFactory()
        val cfg = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://localhost:2375")
                .withDockerTlsVerify(false)
                .build()
        DockerClientBuilder.getInstance(cfg)
                .withDockerCmdExecFactory(cmds)
                .build()
    }


    bind<HazelcastCluster>() with scoped(TestScope).singleton {
        HazelcastCluster(instance(), Duration.ofSeconds(30)).apply { start() }
    }

    bind<HazelcastConfig>() with multiton { name : String ->
        val cluster : HazelcastCluster = instance()
        HazelcastConfig(name, cluster.ips)
    }

    bind<HazelcastDatastore>() with multiton { name : String ->
        HazelcastDatastore(factory<String,HazelcastConfig>()(name), instance())
    }
}

@ExperimentalCoroutinesApi
val kodeinTest : Kodein.Module = Kodein.Module("test") {
    import(kodeinDatastores)
    import(kodeinMongo)
    import(kodeinHazelcast)
    bind<Datastore>() with multiton { type: String, name: String ->
        when (type) {
            MongodatastoreConfig.TYPE -> factory<String,MongoDatastore>()(name)
            HazelcastConfig.TYPE -> factory<String,HazelcastDatastore>()(name)
            else -> DONTDOTHIS()
        }
    }
    constant("test-datastores") with listOf<String>("hazelcast")
}

object TestEnv {
    val datastores = listOf("hazelcast")
}

fun testDatastores() = listOf("hazelcast")

abstract class BaseScope<T> : Scope<T> {
    private val mapRegistry = HashMap<T, ScopeRegistry>()
    private val log = KotlinLogging.logger { }
    override fun getRegistry(context: T): ScopeRegistry = synchronized(mapRegistry) {
        log.info { "register $context" }
        mapRegistry[context] ?: run {
            val scopeRegistry = StandardScopeRegistry()
            mapRegistry[context] = scopeRegistry
            log.info { "register $context with $scopeRegistry" }
            scopeRegistry
        }
    }

    fun close(context: T) {
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

object TestScope : BaseScope<Any>()
object ProjectScope : BaseScope<Any>()