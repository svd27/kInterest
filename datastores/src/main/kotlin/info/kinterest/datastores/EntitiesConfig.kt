package info.kinterest.datastores

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.config4k.extract

class EntitiesConfig(cfg:Config) {
    data class EntityConfig(val name:String, val livesin:Set<String>)

    val entities : List<EntityConfig>

    init {
        entities = cfg.extract("entities")
    }
}

fun main() {
    EntitiesConfig(ConfigFactory.empty())
}