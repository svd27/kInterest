package info.kinterest.docker.hazelcast

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.ExposedPorts
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import info.kinterest.docker.client.BaseContainer
import info.kinterest.docker.client.LogAcceptor
import info.kinterest.docker.client.LogWaitStrategy
import info.kinterest.functional.Try
import info.kinterest.functional.getOrElse
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.dom4j.Element
import org.dom4j.Node
import org.dom4j.io.SAXReader
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.lang.Runnable
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.Executors
import javax.xml.bind.JAXBElement
import javax.xml.parsers.SAXParser
import kotlin.random.Random

class HazelcastCluster(private val client: DockerClient, private val duration: Duration = Duration.ofSeconds(10)) {
    private val log = KotlinLogging.logger { }
    private val coroutineDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
    private val scope : CoroutineScope = CoroutineScope(coroutineDispatcher)
    val ips: List<String>
        get() = containers.map {
            "${it.key.ipAddress}:${it.value}"
        }
    val containers: Map<BaseContainer,Int>

    init {
        val rnd = Random(System.currentTimeMillis())
        val nwname = "hccpnw${rnd.nextInt(99999)}"
        val nw = client.createNetworkCmd().withDriver("bridge").withName(nwname).exec().id
        val conts = mutableMapOf<BaseContainer,Int>()
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
            val port = portStart+it
            val url = prepareXml(port)

            conts += BaseContainer(client = client, image = "hazelcast/hazelcast",
                    network = nw,
                    binds = listOf("/opt/cluster" to listOf(url)),
                    env = listOf("JAVA_OPTS=-Dhazelcast.config=/opt/cluster/hazelcast-cluster.xml"),
                    exposedPorts = ExposedPorts(ExposedPort(port)),
                    portBindings = listOf(PortBinding(Ports.Binding("localhost", "$port/tcp"), ExposedPort(port)))) to (port)
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

    fun prepareXml(port: Int): URL = run {
        val sax = SAXReader()
        val url = javaClass.classLoader.getResource("hazelcast-cluster.xml")
        val doc = sax.read(javaClass.classLoader.getResourceAsStream("hazelcast-cluster.xml"))

        doc.rootElement.element("network").element("port").run {
            text = "$port"
            attribute("auto-increment").value = "false"
        }

        val tmp = Files.createTempDirectory(".kinterest.docker").apply {
            toFile().deleteOnExit()
        }
        val f = if (url.path.matches("/[A-Z]:/.*".toRegex())) Paths.get(url.path.substring(1)).fileName.toString() else
            Paths.get(url.toURI()).fileName.toString()

        log.info { f }

        val dest = tmp.resolve(f)
        log.info { "copy ${url} -> $dest" }
        val fileWriter = FileWriter(dest.toFile())
        doc.write(fileWriter)
        fileWriter.flush()
        fileWriter.close()

        dest.toUri().toURL().apply { log.debug { "returning $this" } }
    }
}