package info.kinterest.docker.hazelcast.jet

import com.github.dockerjava.api.DockerClient
import info.kinterest.docker.client.BaseContainer
import info.kinterest.docker.client.LogAcceptor
import info.kinterest.docker.client.LogWaitStrategy
import info.kinterest.functional.Try
import info.kinterest.functional.getOrElse
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.random.Random

class HazelcastJetCluster(private val client: DockerClient, private val duration: Duration = Duration.ofSeconds(50)) {
    private val log = KotlinLogging.logger { }
    private val coroutineDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
    private val scope: CoroutineScope = CoroutineScope(coroutineDispatcher)
    val hostenv = System.getenv("DOCKER_CONTAINER_HOST")
    val docker_ip = if (hostenv!=null && hostenv.isNotEmpty())
        hostenv
    else "host.docker.internal"
    val ips: List<String>
        get() = containers.map {
            "$docker_ip:${it.value}"
        }
    val containers: Map<BaseContainer, Int>

    init {
        log.info { "docker ip: $docker_ip" }
        log.info { this::class.java.classLoader.getResources("hazelcast/server/libs").toList().map { it.toURI() } }
        val rnd = Random(System.currentTimeMillis())
        val nwname = "hccpnw${rnd.nextInt(99999)}"
        val nw = client.createNetworkCmd().withDriver("bridge").withName(nwname).exec().id
        val conts = mutableMapOf<BaseContainer, Int>()
        Runtime.getRuntime().addShutdownHook(Thread(object : Runnable {
            override fun run() {
                Try {
                    conts.forEach {
                        log.debug { "stopping ${it.key.container}" }
                        it.key.stopContainer()
                    }
                }
                Try { client.removeNetworkCmd(nw).exec() }.fold({ log.warn(it) { } }) { log.debug { "removed nw $nw" } }
            }
        }))

        val portStart = rnd.nextInt(32000, 40000)
        repeat(3) {
            val port = portStart + it

            conts += BaseContainer(client = client, image = "hazelcast/hazelcast-jet",
                    network = nw,
                    //CLASSPATH_DEFAULT
                    env = listOf(
                            "JAVA_OPTS=-Dhazelcast.local.publicAddress=$docker_ip:$port -Dhazelcast.config=/opt/cluster/hazelcast-cluster-jet.xml",
                            "CLASSPATH=/opt/hazelcast-libs/jet-fat.jar"
                    ),
                    binds = listOf("/opt/cluster" to listOf(javaClass.classLoader.getResource("hazelcast-cluster-jet.xml")),
                            "/opt/hazelcast-libs" to listOf(javaClass.classLoader.getResource("jet-fat.jar"))),
                    //exposedPorts = ExposedPorts(ExposedPort(port)),
                    //portBindings = listOf(PortBinding(Ports.Binding("localhost", "$port/tcp"),  ExposedPort(5701)))
            ) to (port)

        }

        containers = conts.toMap()
    }

    fun start() {
        log.debug { containers.keys }
        val asyncs = containers.keys.map {
            scope.async(coroutineDispatcher) {
                log.debug { "launching: ${it.container}" }
                it.start(LogWaitStrategy(duration = duration, acceptor = LogAcceptor.string("is STARTED")))
                it
            }
        }.apply { log.info { "created deferred $this" } }
        runBlocking(coroutineDispatcher) { asyncs.awaitAll() }

        containers.keys.firstOrNull()?.waitForLog(LogAcceptor.regex(".*CPMember.*uuid=.*, address=.*:.*- LEADER.*".toRegex()), duration)
                ?: throw IllegalStateException()


    }

    fun stop() {
        containers.keys.forEach { Try { it.stopContainer() }.getOrElse { log.warn(it) { } } }
    }
}