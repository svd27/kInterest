package info.kinterest.datastores.hazelcast

import com.hazelcast.core.HazelcastJsonValue
import com.hazelcast.internal.json.Json
import com.hazelcast.internal.json.JsonArray
import com.hazelcast.internal.json.JsonObject
import com.hazelcast.internal.json.WriterConfig
import com.hazelcast.map.AbstractEntryProcessor

class FieldsSetter(private val settersJson: String) : AbstractEntryProcessor<Any, HazelcastJsonValue>() {
    override fun process(entry: Map.Entry<Any, HazelcastJsonValue>): Any {
        val inJson = Json.parse(settersJson).asObject()
        val json = Json.parse(entry.value.toString()).asObject()
        val res = JsonObject()
        for (key in inJson.names()) {
            res.set(key, json.get(key))
            json.set(key, inJson.get(key))
        }
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
        (entry as java.util.Map.Entry<Any, HazelcastJsonValue>).value = HazelcastJsonValue(json.toString())
        return res.toString(WriterConfig.MINIMAL)
    }
}

class FieldsGetter(private val getters: Set<String>) : AbstractEntryProcessor<Any, HazelcastJsonValue>() {
    override fun process(entry: Map.Entry<Any, HazelcastJsonValue>): Any {
        val json = Json.parse(entry.value.toString()).asObject()
        val res = JsonObject()
        getters.forEach { json.get(it)?.let { v -> res.set(it, v) }?:res.set(it, Json.NULL) }
        return res.toString(WriterConfig.MINIMAL)
    }
}

class AddRelations(private val relation: String, private val inJson:String) : AbstractEntryProcessor<Any, HazelcastJsonValue>() {
    override fun process(entry: MutableMap.MutableEntry<Any, HazelcastJsonValue>): Any {
        val input = Json.parse(inJson).asArray()
        val json = Json.parse(entry.value.toString()).asObject()
        val outgoing = json.get(METAINFO).asObject().get(RELATIONSKEY).asObject().get(OUTGOING).asObject()
        val relArr = outgoing.get(relation)?.asArray() ?: JsonArray()
        input.forEach {el ->
            if(el !in relArr)
              relArr.add(el)
        }
        outgoing.set(relation, relArr)
        entry.setValue(HazelcastJsonValue(json.toString()))

        return ""
    }

    val METAINFO: String = "_metaInfo"
    val RELATIONSKEY = "relations"
    val OUTGOING = "outgoing"
    val INCOMING = "incoming"
}

class SetRelations(private val relation: String, private val inJson:String) : AbstractEntryProcessor<Any, HazelcastJsonValue>() {
    override fun process(entry: MutableMap.MutableEntry<Any, HazelcastJsonValue>): Any {
        val input = Json.parse(inJson).asArray()
        val json = Json.parse(entry.value.toString()).asObject()
        val outgoing = json.get(METAINFO).asObject().get(RELATIONSKEY).asObject().get(OUTGOING).asObject()

        outgoing.set(relation, input)
        entry.setValue(HazelcastJsonValue(json.toString()))

        return ""
    }


    val METAINFO: String = "_metaInfo"
    val RELATIONSKEY = "relations"
    val OUTGOING = "outgoing"
    val INCOMING = "incoming"
}


class RemoveRelations(private val relation: String, private val inJson:String) : AbstractEntryProcessor<Any, HazelcastJsonValue>() {
    override fun process(entry: MutableMap.MutableEntry<Any, HazelcastJsonValue>): Any {
        val input = Json.parse(inJson).asArray().map { it.asObject().get("toId") to it.asObject().get("toType") }
        val json = Json.parse(entry.value.toString()).asObject()
        val outgoing = json.get(METAINFO).asObject().get(RELATIONSKEY).asObject().get(OUTGOING).asObject()
        val rels = outgoing.get(relation).asArray()
        for(v in input) {
            val idx = rels.indexOfFirst { it.asObject().get("toId") ==  v.first && it.asObject().get("toType") == v.second}
            if(idx>= 0) rels.remove(idx)
        }
        outgoing.set(relation, rels)

        entry.setValue(HazelcastJsonValue(json.toString()))

        return ""
    }

    val METAINFO: String = "_metaInfo"
    val RELATIONSKEY = "relations"
    val OUTGOING = "outgoing"
    val INCOMING = "incoming"
}

class GetRelations(private val relation: String) : AbstractEntryProcessor<Any, HazelcastJsonValue>() {
    override fun process(entry: MutableMap.MutableEntry<Any, HazelcastJsonValue>): Any {
        val json = Json.parse(entry.value.toString()).asObject()
        val outgoing = json.get(METAINFO).asObject().get(RELATIONSKEY).asObject().get(OUTGOING).asObject()
        val arr = outgoing.get(relation)
        return HazelcastJsonValue(arr.toString())
    }

    val METAINFO: String = "_metaInfo"
    val RELATIONSKEY = "relations"
    val OUTGOING = "outgoing"
    val INCOMING = "incoming"
}

class AddIncomingRelations(private val relation: String, private val inJson:String) : AbstractEntryProcessor<Any, HazelcastJsonValue>() {
    override fun process(entry: MutableMap.MutableEntry<Any, HazelcastJsonValue>): Any {
        val input = Json.parse(inJson).asArray()
        val json = Json.parse(entry.value.toString()).asObject()
        val incoming = json.get(METAINFO).asObject().get(RELATIONSKEY).asObject().get(INCOMING).asObject()
        val relArr = incoming.get(relation)?.asArray() ?: JsonArray()
        input.forEach {el ->
            if(el !in relArr)
              relArr.add(el)
        }
        incoming.set(relation, relArr)
        entry.setValue(HazelcastJsonValue(json.toString()))

        return ""
    }

    val METAINFO: String = "_metaInfo"
    val RELATIONSKEY = "relations"
    val OUTGOING = "outgoing"
    val INCOMING = "incoming"
}

class SetIncomingRelations(private val relation: String, private val inJson:String) : AbstractEntryProcessor<Any, HazelcastJsonValue>() {
    override fun process(entry: MutableMap.MutableEntry<Any, HazelcastJsonValue>): Any {
        val input = Json.parse(inJson).asArray()
        val json = Json.parse(entry.value.toString()).asObject()
        val incoming = json.get(METAINFO).asObject().get(RELATIONSKEY).asObject().get(INCOMING).asObject()

        incoming.set(relation, input)
        entry.setValue(HazelcastJsonValue(json.toString()))

        return ""
    }

    val METAINFO: String = "_metaInfo"
    val RELATIONSKEY = "relations"
    val OUTGOING = "outgoing"
    val INCOMING = "incoming"
}

class RemoveIncomingRelations(private val relation: String, private val inJson:String) : AbstractEntryProcessor<Any, HazelcastJsonValue>() {
    override fun process(entry: MutableMap.MutableEntry<Any, HazelcastJsonValue>): Any {
        val input = Json.parse(inJson).asArray().map { it.asObject().get("toId") to it.asObject().get("toType") }
        val json = Json.parse(entry.value.toString()).asObject()
        val incoming = json.get(METAINFO).asObject().get(RELATIONSKEY).asObject().get(INCOMING).asObject()
        val rels = incoming.get(relation).asArray()
        for(v in input) {
            val idx = rels.indexOfFirst { it.asObject().get("toId") ==  v.first && it.asObject().get("toType") == v.second}
            if(idx>= 0) rels.remove(idx)
        }
        incoming.set(relation, rels)

        entry.setValue(HazelcastJsonValue(json.toString()))

        return ""
    }

    val METAINFO: String = "_metaInfo"
    val RELATIONSKEY = "relations"
    val OUTGOING = "outgoing"
    val INCOMING = "incoming"
}



class RetrieveType() : AbstractEntryProcessor<Any,HazelcastJsonValue>() {
    override fun process(entry: MutableMap.MutableEntry<Any, HazelcastJsonValue>): Any? {
        @Suppress("SENSELESS_COMPARISON")
        if(entry.value!= null) {
            val json = Json.parse(entry.value.toString()).asObject()
            return json.get(METAINFO).asObject().getString(TYPE, null)
        }
        return null
    }

    val METAINFO: String = "_metaInfo"
    val TYPE = "type"
    val RELATIONSKEY = "relations"
    val OUTGOING = "outgoing"
    val INCOMING = "incoming"
}



