package info.kinterest.docker.mongo

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.*
import info.kinterest.docker.client.BaseContainer
import info.kinterest.docker.client.LogAcceptor
import info.kinterest.docker.client.LogWaitStrategy
import info.kinterest.functional.getOrElse
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.random.Random

class MongoCluster(private val client: DockerClient, version: String = "latest", private val duration: Duration = Duration.ofSeconds(25)) {
    private val log = KotlinLogging.logger { }
    private val coroutineDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
    private val scope : CoroutineScope = CoroutineScope(coroutineDispatcher)

    val containers : Map<BaseContainer,Int>
    var master : BaseContainer? = null
    val m1 : BaseContainer

    init {
        val rnd = Random(System.currentTimeMillis())
        val nwname = "mongonw${rnd.nextInt(99999)}"
        val nw = client.createNetworkCmd().withDriver("bridge").withName(nwname).exec().id

        val portStart = rnd.nextInt(32000, 40000)
        var port = portStart
        val m1 =  BaseContainer(client, "mongo", version,
                exposedPorts = ExposedPorts(ExposedPort(port)),
                aliases = listOf("M1"), network = nw, cmd = listOf("--replSet", "rs0" ,"--bind_ip", "localhost,M1")
                , portBindings = listOf(PortBinding(Ports.Binding("M1", "$port/tcp"), ExposedPort(27017, InternetProtocol.TCP)))
        ) to port
        this.m1 = m1.first

        port++
        val m2 =  BaseContainer(client, "mongo", version,
                exposedPorts = ExposedPorts(ExposedPort(port)),
                aliases = listOf("M2"), network = nw, cmd = listOf("--replSet", "rs0" ,"--bind_ip", "localhost,M2")
                , portBindings = listOf(PortBinding(Ports.Binding("M2", "$port/tcp"), ExposedPort(27017, InternetProtocol.TCP)))
        ) to port
        port++
        val m3 =  BaseContainer(client, "mongo", version,
                exposedPorts = ExposedPorts(ExposedPort(port)),
                aliases = listOf("M3"), network = nw, cmd = listOf("--replSet", "rs0" ,"--bind_ip", "localhost,M3")
                , portBindings = listOf(PortBinding(Ports.Binding("M3", "$port/tcp"), ExposedPort(27017, InternetProtocol.TCP)))
        ) to port
        containers = mapOf(m1, m2, m3)
        Runtime.getRuntime().addShutdownHook(Thread {
            containers.keys.forEach(BaseContainer::stopContainer)
            client.removeNetworkCmd(nw).exec()
        })
    }

    fun start() {
        containers.forEach {
            it.key.start(LogWaitStrategy(duration, LogAcceptor.regex(".*Marking collection local.oplog.rs as collection version.*".toRegex())))
        }

        m1.exec(listOf("/bin/bash", "-c",
                """mongo --eval 'printjson(rs.initiate({_id:"rs0",members:[{_id:0,host:"M1"},{_id:1,host:"M2"},{_id:2,host:"M3"}]}))' --quiet"""), duration = duration)

        master = runBlocking {
            log.debug { "checking for ${duration.toMillis()} ms" }
            withTimeout(duration.toMillis()) {
                while (true) {
                    containers.forEach { container ->
                        log.trace { "checking ${container.key.container} for master" }
                        val res = container.key.exec(listOf("/bin/bash", "-c", """mongo --eval "printjson(rs.isMaster())""""), duration = duration).getOrElse {
                            log.warn(it) {  }
                            listOf()
                        }
                        log.trace { res.map { it.split("\n").firstOrNull { "ismaster" in it } } }
                        if (res.any { it.split("\n").any { "ismaster" in it && "true" in it } }) return@withTimeout container.key
                    }
                    delay(50)
                }
            }
        } as? BaseContainer
                ?: throw IllegalStateException("no master found")
        log.info { "selected master $master" }
    }

    val mongoUri : String get() = "mongodb://${master!!.ipAddress}:${containers[master!!]}"
    val ip get() = master!!.ipAddress
    val port get() = containers[master]
}