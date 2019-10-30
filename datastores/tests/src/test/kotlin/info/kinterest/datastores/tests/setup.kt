package info.kinterest.datastores.tests

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import info.kinterest.DONTDOTHIS
import info.kinterest.datastore.Datastore
import info.kinterest.datastore.DatastoreConfig
import info.kinterest.datastores.kodeinDatastores
import info.kinterest.datastores.hazelcast.HazelcastConfig
import info.kinterest.datastores.hazelcast.HazelcastDatastore
import info.kinterest.datastores.mongo.MongoDatastore
import info.kinterest.datastores.mongo.MongodatastoreConfig
import info.kinterest.docker.client.DockerClientConfigProvider
import info.kinterest.docker.hazelcast.HazelcastCluster
import info.kinterest.docker.mongo.MongoCluster
import io.kotlintest.AbstractProjectConfig
import io.kotlintest.Spec
import io.kotlintest.TestCase
import io.kotlintest.provided.ProjectConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import mu.KotlinLogging
import org.kodein.di.Kodein
import org.kodein.di.bindings.*
import org.kodein.di.generic.*
import java.lang.IllegalStateException
import java.time.Duration
import kotlin.math.log

interface KodeinCloseable<T> : ScopeCloseable {
    val content: T

    operator fun invoke(): T = content
}


@ExperimentalCoroutinesApi
val kodeinMongo = Kodein.Module(name = "mongo") {
    val log = KotlinLogging.logger { }
    bind<MongoCluster>() with scoped(ProjectScope).singleton {
        MongoCluster(instance(), duration = Duration.ofSeconds(30)).apply {
            start()
            log.info { "cluster with $ip and $port started" }
        }
    }
    bind<MongodatastoreConfig>() with scoped(ProjectScope).multiton { name : String ->
        val cluster : MongoCluster = instance()
        MongodatastoreConfig(name, cluster.ip, cluster.port?:throw IllegalStateException("no port defined"))
    }
    bind<MongoDatastore>() with scoped(ProjectScope).multiton {
        name : String -> MongoDatastore(instance(arg = name), instance())
    }

    bind<MongoDatastore>() with scoped(ProjectScope).multiton {
        cfg : MongodatastoreConfig -> MongoDatastore(cfg, instance())
    }
}


@ExperimentalCoroutinesApi
val kodeinHazelcast = Kodein.Module("hazelcast") {
    bind<HazelcastCluster>() with scoped(ProjectScope).singleton {
        HazelcastCluster(instance(), Duration.ofSeconds(30)).apply { start() }
    }

    bind<HazelcastConfig>() with scoped(ProjectScope).multiton { name : String ->
        val cluster : HazelcastCluster = instance()
        HazelcastConfig(name, cluster.ips)
    }

    bind<HazelcastDatastore>() with scoped(ProjectScope).multiton { name : String ->
        HazelcastDatastore(factory<String,HazelcastConfig>()(name), instance())
    }

    bind<HazelcastDatastore>() with scoped(ProjectScope).multiton { cfg : HazelcastConfig ->
        HazelcastDatastore(cfg, instance())
    }
}

@ExperimentalCoroutinesApi
val kodeinTest : Kodein = Kodein {
    val log = KotlinLogging.logger {  }
    import(kodeinDatastores)
    import(kodeinMongo)
    import(kodeinHazelcast)
    //val dh = System.getenv("DOCKER_HOST")
    log.info { System.getenv() }
    bind<DockerClient>() with scoped(ProjectScope).singleton {
        val cmds: DockerCmdExecFactory = NettyDockerCmdExecFactory()
        val cfg = DockerClientConfigProvider.config()
        val client = DockerClientBuilder.getInstance(cfg)
                .withDockerCmdExecFactory(cmds)
                .build()
        object : ScopeCloseable, DockerClient by client {
            override fun close() {
                log.debug { "not closing client $client" }
                //client.close()
            }
        }
    }

    bind<Datastore>() with scoped(ProjectScope).multiton { type: String, name: String ->
        when (type) {
            MongodatastoreConfig.TYPE -> factory<String,MongoDatastore>()(name)
            HazelcastConfig.TYPE -> factory<String,HazelcastDatastore>()(name)
            else -> DONTDOTHIS()
        } as Datastore
    }

    bind<DatastoreConfig>() with scoped(ProjectScope).multiton { type: String, name: String ->
        when (type) {
            MongodatastoreConfig.TYPE -> factory<String,MongodatastoreConfig>()(name)
            HazelcastConfig.TYPE -> factory<String,HazelcastConfig>()(name)
            else -> DONTDOTHIS()
        }
    }
    constant("test-datastores") with listOf<String>("hazelcast")
}

object TestScope : BaseScope<TestCase>()
object SpecScope : BaseScope<Spec>()
object ProjectScope : Scope<ProjectConfig> {
    val registry : ScopeRegistry = StandardScopeRegistry()

    override fun getRegistry(context: ProjectConfig): ScopeRegistry = registry
}

abstract class BaseScope<T> : Scope<T> {
    private val mapRegistry = HashMap<T, ScopeRegistry>()
    private val log = KotlinLogging.logger { }
    override fun getRegistry(context: T): ScopeRegistry = synchronized(mapRegistry) {
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
