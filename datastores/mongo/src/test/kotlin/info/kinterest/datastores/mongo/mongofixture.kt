package info.kinterest.datastores.mongo

import de.flapdoodle.embed.mongo.MongodExecutable
import de.flapdoodle.embed.mongo.MongodProcess
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.runtime.Network
import info.kinterest.functional.Try
import org.spekframework.spek2.dsl.Root

object MonoEmbed {
    @JvmStatic
    val starter : MongodStarter = MongodStarter.getDefaultInstance()
}

class MongoEmbed(val mongodExecutable: MongodExecutable, val mongodProcess: MongodProcess, val cfg:MongodatastoreConfig)

fun Root.setUpMongo() {
    val ip = "localhost"
    val port = 27027
    val cfg = MongodConfigBuilder().version(Version.Main.PRODUCTION).net(Net(ip, port, Network.localhostIsIPv6())).build()
    val mongo =Try {
        val mongodExecutable = MonoEmbed.starter.prepare(cfg)
        val mongodProcess = mongodExecutable.start()
        MongoEmbed(mongodExecutable, mongodProcess, MongodatastoreConfig("test", ip, port))
    } .fold({throw it}, {it})

    val obj by memoized(factory = {mongo}, destructor = {it.mongodProcess.stop(); it.mongodExecutable.stop()})
    val mongodatastoreCfg : MongodatastoreConfig by memoized { mongo.cfg }
}
