package com.astramesh.data.repository

import com.astramesh.common.AstraResult
import com.astramesh.core.NodeId
import com.astramesh.domain.model.Route
import com.astramesh.domain.repository.RoutingRepository
import com.astramesh.storage.dao.RouteDao
import com.astramesh.storage.entity.RouteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoutingRepositoryImpl(
    private val routeDao: RouteDao
) : RoutingRepository {

    override fun observeRoutes(): Flow<List<Route>> {
        return routeDao.observeActiveRoutes().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getRouteFor(destination: NodeId): Route? {
        return routeDao.getActiveRoute(destination.value)?.toDomain()
    }

    override suspend fun updateRoute(route: Route): AstraResult<Unit> = AstraResult.of {
        routeDao.insertOrUpdate(route.toEntity())
    }

    override suspend fun evictExpiredRoutes(currentTimeMillis: Long): AstraResult<Int> = AstraResult.of {
        routeDao.evictExpired(currentTimeMillis)
    }

    private fun RouteEntity.toDomain(): Route {
        return Route(
            destination = NodeId(destination),
            nextHop = NodeId(nextHop),
            cost = cost,
            hopCount = hopCount,
            sequenceNumber = sequenceNumber,
            expireTimestamp = expireTimestamp
        )
    }

    private fun Route.toEntity(): RouteEntity {
        return RouteEntity(
            destination = destination.value,
            nextHop = nextHop.value,
            cost = cost,
            hopCount = hopCount,
            sequenceNumber = sequenceNumber,
            expireTimestamp = expireTimestamp
        )
    }
}
