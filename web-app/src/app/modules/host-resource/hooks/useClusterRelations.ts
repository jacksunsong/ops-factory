import { useState, useCallback } from 'react'
import { GATEWAY_URL, gatewayHeaders } from '../../../../config/runtime'
import { useUser } from '../../../platform/providers/UserContext'
import type { ClusterRelation, ClusterGraphData } from '../../../../types/host'

function apiBase() { return `${GATEWAY_URL}/cluster-relations` }

export function useClusterRelations() {
    const { userId } = useUser()
    const [relations, setRelations] = useState<ClusterRelation[]>([])
    const [clusterGraphData, setClusterGraphData] = useState<ClusterGraphData>({ nodes: [], edges: [] })
    const [loading, setLoading] = useState(false)

    const fetchRelations = useCallback(async (clusterId?: string) => {
        setLoading(true)
        try {
            const params = new URLSearchParams()
            if (clusterId) params.set('clusterId', clusterId)
            const qs = params.toString()
            const res = await fetch(`${apiBase()}${qs ? '?' + qs : ''}`, { headers: gatewayHeaders(userId) })
            const data = await res.json()
            setRelations(data.relations || [])
        } catch (err) {
            console.error('Failed to fetch cluster relations', err)
        } finally {
            setLoading(false)
        }
    }, [userId])

    const fetchClusterGraph = useCallback(async (groupId?: string) => {
        setLoading(true)
        try {
            const params = new URLSearchParams()
            if (groupId) params.set('groupId', groupId)
            const qs = params.toString()
            const res = await fetch(`${apiBase()}/graph${qs ? '?' + qs : ''}`, { headers: gatewayHeaders(userId) })
            const data = await res.json()
            setClusterGraphData(data?.nodes ? data : { nodes: [], edges: [] })
        } catch (err) {
            console.error('Failed to fetch cluster graph data', err)
        } finally {
            setLoading(false)
        }
    }, [userId])

    const createRelation = useCallback(async (body: Partial<ClusterRelation>) => {
        const res = await fetch(apiBase(), {
            method: 'POST',
            headers: gatewayHeaders(userId),
            body: JSON.stringify(body),
        })
        const data = await res.json()
        if (data.success) {
            await Promise.all([fetchRelations(), fetchClusterGraph()])
            return data.relation
        }
        throw new Error(data.error || 'Failed to create cluster relation')
    }, [userId, fetchRelations, fetchClusterGraph])

    const updateRelation = useCallback(async (id: string, body: Partial<ClusterRelation>) => {
        const res = await fetch(`${apiBase()}/${id}`, {
            method: 'PUT',
            headers: gatewayHeaders(userId),
            body: JSON.stringify(body),
        })
        const data = await res.json()
        if (data.success) {
            await fetchRelations()
            return data.relation
        }
        throw new Error(data.error || 'Failed to update cluster relation')
    }, [userId, fetchRelations])

    const deleteRelation = useCallback(async (id: string) => {
        const res = await fetch(`${apiBase()}/${id}`, {
            method: 'DELETE',
            headers: gatewayHeaders(userId),
        })
        const data = await res.json()
        if (data.success) {
            await fetchRelations()
            return true
        }
        throw new Error(data.error || 'Failed to delete cluster relation')
    }, [userId, fetchRelations])

    return {
        relations, clusterGraphData, loading,
        fetchRelations, fetchClusterGraph,
        createRelation, updateRelation, deleteRelation,
    }
}
