package info.kinterest

import info.kinterest.entity.KIEntity
import info.kinterest.filter.FilterWrapper
import info.kinterest.projection.ParentProjection
import info.kinterest.projection.ParentProjectionResult

class Query<ID:Any,E:KIEntity<ID>>(val f : FilterWrapper<ID,E>, val projection: ParentProjection<ID,E>)

class QueryResult<ID:Any,E:KIEntity<ID>>(val query: Query<ID,E>, val result: ParentProjectionResult<ID,E>)

