import { useState, useCallback } from 'react'
import { csvToObjects } from '../../../../utils/csvExport'
import type { HostGroup, Cluster, Host, HostCreateRequest, BusinessService, ClusterType, BusinessType } from '../../../../types/host'
import type { SopCreateRequest } from '../../../../types/sop'
import type { WhitelistCommand } from '../../../../types/commandWhitelist'

export type ImportType =
    | 'ClusterTypes'
    | 'BusinessTypes'
    | 'HostGroups'
    | 'Clusters'
    | 'Hosts'
    | 'BusinessServices'
    | 'Relations'
    | 'SOPs'
    | 'Whitelist'

export interface ImportError {
    row: number
    message: string
}

export interface ImportProgress {
    current: number
    total: number
    phase: string
}

export interface ImportResult {
    success: number
    failed: number
    errors: ImportError[]
}

interface ImportDeps {
    // Lookups
    fetchGroups: () => Promise<void>
    fetchAllClusters: () => Promise<void>
    fetchAllHosts: () => Promise<void>
    fetchHostRelations: () => Promise<void>
    fetchBusinessServices: () => Promise<void>
    fetchGraph: (clusterId?: string, groupId?: string) => Promise<void>
    fetchWhitelist: () => Promise<void>

    groups: HostGroup[]
    clusters: Cluster[]
    allHosts: Host[]
    businessServices: BusinessService[]
    clusterTypes: ClusterType[]
    businessTypes: BusinessType[]

    // Create functions
    createGroup: (body: Partial<HostGroup>) => Promise<HostGroup>
    updateGroup: (id: string, body: Partial<HostGroup>) => Promise<HostGroup>
    createCluster: (body: Partial<Cluster>) => Promise<Cluster>
    createHost: (body: HostCreateRequest) => Promise<Host>
    createBusinessService: (body: Partial<BusinessService>) => Promise<BusinessService>
    createRelation: (body: Partial<import('../../../../types/host').HostRelation>) => Promise<unknown>
    createClusterType: (body: Partial<ClusterType>) => Promise<unknown>
    createBusinessType: (body: Partial<BusinessType>) => Promise<unknown>
    createSop: (body: SopCreateRequest) => Promise<unknown>
    addWhitelistCommand: (cmd: WhitelistCommand) => Promise<boolean>
}

export function useResourceImport(deps: ImportDeps) {
    const [importing, setImporting] = useState(false)
    const [progress, setProgress] = useState<ImportProgress | null>(null)

    const importCsv = useCallback(async (type: ImportType, csvText: string): Promise<ImportResult> => {
        const rows = csvToObjects(csvText)
        if (rows.length === 0) {
            return { success: 0, failed: 0, errors: [{ row: 0, message: 'No data rows found' }] }
        }

        setImporting(true)
        setProgress({ current: 0, total: rows.length, phase: type })

        const errors: ImportError[] = []
        let success = 0

        // Build lookup maps from current data
        const groupNameToId = new Map(deps.groups.map(g => [g.name, g.id]))
        const groupCodeToId = new Map(deps.groups.map(g => [g.code ?? '', g.id]))
        const clusterNameToId = new Map(deps.clusters.map(c => [c.name, c.id]))
        const clusterTypeNameToCode = new Map(deps.clusterTypes.map(ct => [ct.name, ct.code]))
        const businessTypeNameToId = new Map(deps.businessTypes.map(bt => [bt.name, bt.id]))
        const hostNameToId = new Map(deps.allHosts.map(h => [h.name, h.id]))
        const bsNameToId = new Map(deps.businessServices.map(bs => [bs.name, bs.id]))

        for (let i = 0; i < rows.length; i++) {
            const row = rows[i]
            setProgress({ current: i + 1, total: rows.length, phase: type })

            try {
                switch (type) {
                    case 'ClusterTypes': {
                        await deps.createClusterType({
                            name: row.name,
                            code: row.code,
                            description: row.description || '',
                            knowledge: row.knowledge || '',
                            commandPrefix: row.commandprefix || '',
                            envVariables: row.envvariables
                                ? row.envvariables.split(';').filter(Boolean).map(pair => {
                                    const eq = pair.indexOf('=')
                                    return { key: eq > 0 ? pair.slice(0, eq) : pair, value: eq > 0 ? pair.slice(eq + 1) : '' }
                                })
                                : undefined,
                        })
                        success++
                        break
                    }

                    case 'BusinessTypes': {
                        await deps.createBusinessType({
                            name: row.name,
                            code: row.code,
                            description: row.description || '',
                            knowledge: row.knowledge || '',
                        })
                        success++
                        break
                    }

                    case 'HostGroups': {
                        const created = await deps.createGroup({
                            name: row.name,
                            code: row.code || undefined,
                            description: row.description || '',
                        })
                        groupNameToId.set(row.name, created.id)
                        if (row.code) groupCodeToId.set(row.code, created.id)
                        success++
                        break
                    }

                    case 'Clusters': {
                        const groupId = row.group
                            ? (groupNameToId.get(row.group) || groupCodeToId.get(row.group))
                            : undefined
                        const typeCode = row.type ? (clusterTypeNameToCode.get(row.type) || row.type) : ''
                        if (!groupId && row.group) {
                            errors.push({ row: i + 1, message: `Group "${row.group}" not found` })
                            continue
                        }
                        const created = await deps.createCluster({
                            name: row.name,
                            type: typeCode,
                            purpose: row.purpose || '',
                            groupId,
                            description: row.description || '',
                        })
                        clusterNameToId.set(row.name, created.id)
                        success++
                        break
                    }

                    case 'Hosts': {
                        const clusterId = row.cluster
                            ? clusterNameToId.get(row.cluster)
                            : undefined
                        if (!clusterId && row.cluster) {
                            errors.push({ row: i + 1, message: `Cluster "${row.cluster}" not found` })
                            continue
                        }
                        const created = await deps.createHost({
                            name: row.name,
                            ip: row.ip,
                            port: row.port ? parseInt(row.port, 10) : 22,
                            username: row.username,
                            authType: (row.authtype === 'key' ? 'key' : 'password') as 'password' | 'key',
                            credential: row.credential || '',
                            hostname: row.hostname || undefined,
                            businessIp: row.businessip || undefined,
                            os: row.os || undefined,
                            location: row.location || undefined,
                            business: row.business || undefined,
                            clusterId,
                            purpose: row.purpose || undefined,
                            tags: row.tags ? row.tags.split(';').map(t => t.trim()).filter(Boolean) : [],
                            description: row.description || undefined,
                        })
                        hostNameToId.set(row.name, created.id)
                        success++
                        break
                    }

                    case 'BusinessServices': {
                        const groupId = row.group
                            ? (groupNameToId.get(row.group) || groupCodeToId.get(row.group))
                            : undefined
                        const businessTypeId = row.businesstype
                            ? businessTypeNameToId.get(row.businesstype)
                            : undefined
                        if (!groupId && row.group) {
                            errors.push({ row: i + 1, message: `Group "${row.group}" not found` })
                            continue
                        }
                        const created = await deps.createBusinessService({
                            name: row.name,
                            code: row.code,
                            groupId,
                            businessTypeId,
                            description: row.description || '',
                            tags: row.tags ? row.tags.split(';').map(t => t.trim()).filter(Boolean) : [],
                            priority: row.priority || '',
                            contactInfo: row.contactinfo || '',
                        })
                        bsNameToId.set(row.name, created.id)
                        success++
                        break
                    }

                    case 'Relations': {
                        const sourceBsId = bsNameToId.get(row.sourcenode)
                        const sourceHostId = hostNameToId.get(row.sourcenode)
                        const destHostId = hostNameToId.get(row.destnode)
                        if (!destHostId) {
                            errors.push({ row: i + 1, message: `Target host "${row.destnode}" not found` })
                            continue
                        }
                        if (!sourceBsId && !sourceHostId) {
                            errors.push({ row: i + 1, message: `Source node "${row.sourcenode}" not found as host or business service` })
                            continue
                        }
                        await deps.createRelation({
                            sourceHostId: sourceBsId || sourceHostId!,
                            targetHostId: destHostId,
                            description: row.description || '',
                            sourceType: sourceBsId ? 'business-service' : 'host',
                        })
                        success++
                        break
                    }

                    case 'SOPs': {
                        const tags = row.tags
                            ? row.tags.split(';').map(t => t.trim()).filter(Boolean)
                            : []
                        await deps.createSop({
                            name: row.name,
                            description: row.description || '',
                            version: row.version || '',
                            triggerCondition: row.triggercondition || '',
                            enabled: row.enabled !== 'false',
                            mode: (row.mode === 'natural_language' ? 'natural_language' : 'structured') as 'structured' | 'natural_language',
                            stepsDescription: row.stepsdescription || '',
                            tags,
                        })
                        success++
                        break
                    }

                    case 'Whitelist': {
                        await deps.addWhitelistCommand({
                            pattern: row.pattern,
                            description: row.description || '',
                            enabled: row.enabled !== 'false',
                        })
                        success++
                        break
                    }
                }
            } catch (err) {
                const msg = err instanceof Error ? err.message : String(err)
                errors.push({ row: i + 1, message: msg })
            }
        }

        // Post-import: update parent groups for HostGroups type
        if (type === 'HostGroups') {
            const parentRows = rows.filter(r => r.parentgroup)
            for (let i = 0; i < parentRows.length; i++) {
                const row = parentRows[i]
                const groupId = groupNameToId.get(row.name)
                const parentId = groupNameToId.get(row.parentgroup) || groupCodeToId.get(row.parentgroup)
                if (groupId && parentId) {
                    try {
                        await deps.updateGroup(groupId, { parentId })
                    } catch (err) {
                        errors.push({ row: i + 1, message: `Failed to set parent: ${err instanceof Error ? err.message : String(err)}` })
                    }
                }
            }
        }

        // Refresh data
        try {
            await Promise.all([
                deps.fetchGroups(),
                deps.fetchAllClusters(),
                deps.fetchAllHosts(),
                deps.fetchHostRelations(),
                deps.fetchBusinessServices(),
                deps.fetchGraph(),
                deps.fetchWhitelist(),
            ])
        } catch { /* non-critical refresh failure */ }

        setImporting(false)
        setProgress(null)
        return { success, failed: errors.length, errors }
    }, [deps])

    return { importing, progress, importCsv }
}
