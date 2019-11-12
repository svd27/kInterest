package info.kinterest.query

import info.kinterest.Query
import info.kinterest.QueryResult
import info.kinterest.entity.KIEntity
import kotlinx.coroutines.flow.Flow

interface QueryManager {
    suspend fun<ID:Any,E:KIEntity<ID>> query(q:Query<ID,E>) : Flow<QueryResult<ID,E>>
}