package info.kinterest.datastores

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import info.kinterest.datastore.Datastore
import info.kinterest.datastore.DatastoreConfig
import info.kinterest.datastore.EventManager
import io.github.config4k.extract
import kotlinx.coroutines.ExperimentalCoroutinesApi
import mu.KotlinLogging
import java.lang.IllegalStateException
import kotlin.reflect.KClass

data class FactoryConfig(val type: String, val configClass: String, val datastoreClass:String)

class DatastoreFactory @ExperimentalCoroutinesApi constructor(val events:EventManager) {
    val log = KotlinLogging.logger {  }
    var configs : MutableMap<String,FactoryConfig> = mutableMapOf()

    init {
        this::class.java.classLoader.getResources("datastore.conf").asSequence().forEach {
            log.debug { "found conf" }
            addConfig(it.readText())
        }
    }


    fun addConfig(cfg: String) {
        val config = ConfigFactory.parseString(cfg)
        addConfig(config)
    }

    fun addConfig(cfg:Config) {
        val fc : FactoryConfig = cfg.extract()
        addConfig(fc)
    }

    fun addConfig(cfg:FactoryConfig) {
        log.info { "adding config $cfg" }
        configs[cfg.type] = cfg
    }

    @ExperimentalCoroutinesApi
    fun create(cfg:DatastoreConfig) : Datastore = run {
        val factoryConfig : FactoryConfig = configs[cfg.type]?:throw IllegalStateException()
        val dsClass : KClass<*> = Class.forName(factoryConfig.datastoreClass).kotlin
        val cfgClass : KClass<*> = Class.forName(factoryConfig.configClass).kotlin
        dsClass.constructors.firstOrNull {
            it.parameters.size == 2 && it.parameters[0].type.classifier ==  cfgClass &&
                    it.parameters[1].type.classifier == EventManager::class
        }?.call(cfg, events) as? Datastore ?:throw IllegalStateException()
    }
}