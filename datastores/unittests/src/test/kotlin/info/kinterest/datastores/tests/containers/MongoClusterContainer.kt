package info.kinterest.datastores.tests.containers

import com.mongodb.reactivestreams.client.MongoClients
import info.kinterest.functional.Try
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.kodein.di.bindings.ScopeCloseable
import org.testcontainers.containers.wait.strategy.Wait


class MongoClusterContainer(val mongoVersion:String="latest") : ScopeCloseable {
    val log = KotlinLogging.logger {  }
    val port = 27027
    val mongoUri : String
    val mongoIp : String
    val mongoPort : Int

    var containers: List<KGenericContainer> = listOf()
    init {
        val nw = Network.newNetwork()

        val image = "mongo:$mongoVersion"
        val m1 = KGenericContainer(image)
                .withNetwork(nw)
                .withNetworkAliases("M1")
                .withExposedPorts(port)
                .withCommand("--replSet rs0 --bind_ip localhost,M1")
                .waitingFor(Wait.forLogMessage(".*Marking collection local.oplog.rs as collection version.*\\n", 1))
        val m2 = KGenericContainer(image)
                .withNetwork(nw)
                .withNetworkAliases("M2")
                .withExposedPorts(port)
                .withCommand("--replSet rs0 --bind_ip localhost,M2")
                .waitingFor(Wait.forLogMessage(".*Marking collection local.oplog.rs as collection version.*\\n", 1))

        val m3 = KGenericContainer(image)
                .withNetwork(nw)
                .withNetworkAliases("M3")
                .withExposedPorts(port)
                .withCommand("--replSet rs0 --bind_ip localhost,M3")
                .waitingFor(Wait.forLogMessage(".*Marking collection local.oplog.rs as collection version.*\\n", 1))

        log.debug { "start m1" }
        m1.start()
        log.debug { "start m2" }
        m2.start()
        log.debug { "start m3" }
        m3.start()
        containers = listOf(m1, m2, m3)
        var masterContainer : KGenericContainer? = null

        Try {
            //runBlocking { delay(1000L) }
            /*
            m1.execInContainer("/bin/bash", "-c",
                    "mongo --eval 'printjson(rs.initiate({_id:\"rs0\","
                            + "members:[{_id:0,host:\"M1:$port\"},{_id:1,host:\"M2:$port\"},{_id:2,host:\"M3:$port\"}]}))' "
                            + "--quiet")

             */

            /*
            m1.execInContainer("/bin/bash", "-c",
                    """mongo --eval 'printjson(rs.initiate({_id:"rs0",members:[{_id:0,host:"M1:$port"},{_id:1,host:"M2:$port"},{_id:2,host:\"M3:$port\"}]}))' --quiet""")
             */
            m1.execInContainer("/bin/bash", "-c",
                    """mongo --eval 'printjson(rs.initiate({_id:"rs0",members:[{_id:0,host:"M1"},{_id:1,host:"M2"},{_id:2,host:"M3"}]}))' --quiet""")

            /*
            m1.execInContainer("/bin/bash", "-c",
                    "until mongo --eval \"printjson(rs.isMaster())\" | grep ismaster | grep true > /dev/null 2>&1;"
                            + "do sleep 1;done")

             */


            repeat(50 ) {
                listOf("m1" to m1, "m2" to m2, "m3" to m3).forEach {
                    val res = it.second.execInContainer("/bin/bash", "-c",
                            //"""mongo --eval 'db = db.getSiblingDB("admin"); printjson(db.getCollectionNames())' """.apply { log.info { "query: $this" } }
                            //"""mongo --quite --eval '{"ismaster": 1, "${'$'}db": "admin"}'""".apply { log.info { "query: $this" } }
                            """mongo --eval "printjson(rs.isMaster())""""
                    )
                    val ismaster = res.stdout.split("\n").firstOrNull { it.contains("ismaster") }?:""
                    log.trace { "exec result ${it.first}:\n ${ismaster}" }
                    if("true" in ismaster) {
                        log.trace { "found master ${it.first} returning" }
                        masterContainer = it.second
                        return@Try
                    }
                    //log.info { "exec error ${it.first}:\n${res.stderr}" }
                }
                runBlocking { delay(500) }
            }



        }.fold({throw it}) {Unit}

        val s = """
            mongo --eval "printjson(rs.isMaster())"
            mongo --eval 'printjson(rs.initiate({_id:"rs0","members:[{_id:0,host:"M1:27028"},{_id:1,host:"M2:27028"},{_id:2,host:"M3:27028"}]}))'
            
            mongo --eval 'printjson(rs.initiate({_id:"rs0",members:[{_id:0,host:"M1:27028"},{_id:1,host:"M2:27028"},{_id:2,host:"M3:27028"}]}))'
        """.trimIndent()

        val master = masterContainer
        require(master!=null)
        mongoIp = master.containerIpAddress
        mongoPort = master.getMappedPort(port)+1
        //port + 1 is a weird hack, maybe coz of windows docker toolbox
        mongoUri = "mongodb://${mongoIp}:$mongoPort"
        log.debug { "connecting to $mongoUri" }
        val mongoClient = MongoClients.create(mongoUri)

        runBlocking { mongoClient.listDatabaseNames().asFlow().collect {log.debug { "db: $it" }}
            val session = mongoClient.startSession().awaitLast()
            session.startTransaction()
            session.commitTransaction()
        }

    }

    fun stop() {
        containers.forEach {
            log.info { "shutting dowm ${it.dockerImageName}" }
            it.stop()
        }
    }

    override fun close() = stop()
}