import { useState, useCallback } from 'react'
import { createZip } from '../../../../utils/zipHelper'
import { objectsToCsv } from '../../../../utils/csvExport'
import type { HostGroup, Cluster, Host, BusinessService, HostRelation, ClusterType, BusinessType } from '../../../../types/host'
import type { Sop } from '../../../../types/sop'
import type { WhitelistCommand } from '../../../../types/commandWhitelist'

type EnvVariable = { key: string; value: string }

export function useResourceExport() {
    const [exporting, setExporting] = useState(false)

    const exportAllAsZip = useCallback(async (params: {
        groups: HostGroup[]
        clusters: Cluster[]
        allHosts: Host[]
        hostRelations: HostRelation[]
        businessServices: BusinessService[]
        clusterTypes: ClusterType[]
        businessTypes: BusinessType[]
        whitelistCommands: WhitelistCommand[]
        sops: Sop[]
    }) => {
        setExporting(true)
        try {
            const {
                groups, clusters, allHosts, hostRelations,
                businessServices, clusterTypes, businessTypes,
                whitelistCommands, sops,
            } = params

            // Build lookup maps for ID → name resolution
            const groupMap = new Map(groups.map(g => [g.id, g]))
            const clusterMap = new Map(clusters.map(c => [c.id, c]))

            // 1. Cluster types CSV
            const ctHeaders = [
                { key: 'name' }, { key: 'code' }, { key: 'description' },
                { key: 'knowledge' }, { key: 'commandPrefix' },
                { key: 'envVariables' },
            ]
            const ctRows = clusterTypes.map(ct => ({
                name: ct.name,
                code: ct.code,
                description: ct.description || '',
                knowledge: ct.knowledge || '',
                commandPrefix: ct.commandPrefix || '',
                envVariables: ct.envVariables
                    ? (ct.envVariables as EnvVariable[]).map((v: EnvVariable) => `${v.key}=${v.value}`).join(';')
                    : '',
            }))

            // 2. Business types CSV
            const btHeaders = [
                { key: 'name' }, { key: 'code' }, { key: 'description' },
                { key: 'knowledge' },
            ]
            const btRows = businessTypes.map(bt => ({
                name: bt.name,
                code: bt.code,
                description: bt.description || '',
                knowledge: bt.knowledge || '',
            }))

            // 3. Groups CSV
            const groupHeaders = [
                { key: 'name' }, { key: 'code' }, { key: 'parentGroup' },
                { key: 'description' },
            ]
            const groupRows = groups.map(g => ({
                name: g.name,
                code: g.code || '',
                parentGroup: g.parentId ? (groupMap.get(g.parentId)?.name ?? '') : '',
                description: g.description || '',
            }))

            // 4. Clusters CSV
            const clusterHeaders = [
                { key: 'name' }, { key: 'type' }, { key: 'purpose' },
                { key: 'group' }, { key: 'description' },
            ]
            const clusterRows = clusters.map(c => ({
                name: c.name,
                type: c.type || '',
                purpose: c.purpose || '',
                group: c.groupId ? (groupMap.get(c.groupId)?.name ?? '') : '',
                description: c.description || '',
            }))

            // 5. Hosts CSV
            const hostHeaders = [
                { key: 'name' }, { key: 'hostname' }, { key: 'ip' },
                { key: 'businessIp' }, { key: 'port' }, { key: 'os' },
                { key: 'location' }, { key: 'username' }, { key: 'authType' },
                { key: 'credential' }, { key: 'business' }, { key: 'cluster' },
                { key: 'purpose' }, { key: 'tags' }, { key: 'description' },
            ]
            const hostRows = allHosts.map(h => ({
                name: h.name,
                hostname: h.hostname || '',
                ip: h.ip,
                businessIp: h.businessIp || '',
                port: String(h.port),
                os: h.os || '',
                location: h.location || '',
                username: h.username,
                authType: h.authType,
                credential: '',  // Always empty on export
                business: h.business || '',
                cluster: h.clusterId ? (clusterMap.get(h.clusterId)?.name ?? '') : '',
                purpose: h.purpose || '',
                tags: Array.isArray(h.tags) ? h.tags.join(';') : '',
                description: h.description || '',
            }))

            // 6. Business services CSV
            const bsHeaders = [
                { key: 'name' }, { key: 'code' }, { key: 'group' },
                { key: 'businessType' }, { key: 'description' },
                { key: 'tags' }, { key: 'priority' }, { key: 'contactInfo' },
            ]
            const businessTypeMap = new Map(businessTypes.map(bt => [bt.id, bt]))
            const bsRows = businessServices.map(bs => ({
                name: bs.name,
                code: bs.code,
                group: bs.groupId ? (groupMap.get(bs.groupId)?.name ?? '') : '',
                businessType: bs.businessTypeId ? (businessTypeMap.get(bs.businessTypeId)?.name ?? '') : '',
                description: bs.description || '',
                tags: Array.isArray(bs.tags) ? bs.tags.join(';') : '',
                priority: bs.priority || '',
                contactInfo: bs.contactInfo || '',
            }))

            // 7. Relations CSV — includes both host-host and business-host relations
            const relHeaders = [
                { key: 'sourceNode' }, { key: 'destNode' }, { key: 'description' },
            ]
            const allHostMap = new Map(allHosts.map(h => [h.id, h]))
            const bsMap = new Map(businessServices.map(bs => [bs.id, bs]))

            const relRows: { sourceNode: string; destNode: string; description: string }[] = []

            // Host-host relations
            for (const r of hostRelations) {
                const sourceName = r.sourceType === 'business-service'
                    ? (bsMap.get(r.sourceHostId)?.name ?? '')
                    : (allHostMap.get(r.sourceHostId)?.name ?? '')
                const destName = allHostMap.get(r.targetHostId)?.name ?? ''
                if (sourceName && destName) {
                    relRows.push({
                        sourceNode: sourceName,
                        destNode: destName,
                        description: r.description || '',
                    })
                }
            }

            // Business-service → host relations (via hostIds)
            for (const bs of businessServices) {
                for (const hostId of bs.hostIds) {
                    const hostName = allHostMap.get(hostId)?.name
                    if (hostName) {
                        // Only add if not already covered by hostRelations
                        const exists = relRows.some(r =>
                            r.sourceNode === bs.name && r.destNode === hostName
                        )
                        if (!exists) {
                            relRows.push({
                                sourceNode: bs.name,
                                destNode: hostName,
                                description: '',
                            })
                        }
                    }
                }
            }

            // 8. SOPs CSV
            const sopHeaders = [
                { key: 'name' }, { key: 'description' }, { key: 'version' },
                { key: 'triggerCondition' }, { key: 'enabled' }, { key: 'mode' },
                { key: 'stepsDescription' }, { key: 'tags' },
            ]
            const sopRows = sops.map(s => ({
                name: s.name,
                description: s.description || '',
                version: s.version || '',
                triggerCondition: s.triggerCondition || '',
                enabled: String(s.enabled ?? true),
                mode: s.mode || 'structured',
                stepsDescription: s.stepsDescription || '',
                tags: Array.isArray(s.tags) ? s.tags.join(';') : '',
            }))

            // 9. Whitelist CSV
            const wlHeaders = [
                { key: 'pattern' }, { key: 'description' }, { key: 'enabled' },
            ]
            const wlRows = whitelistCommands.map(cmd => ({
                pattern: cmd.pattern,
                description: cmd.description || '',
                enabled: String(cmd.enabled),
            }))

            // Build CSV files
            const encoder = new TextEncoder()
            const csvFiles = [
                { name: 'cluster_types.csv', data: encoder.encode(objectsToCsv(ctHeaders, ctRows)) },
                { name: 'business_types.csv', data: encoder.encode(objectsToCsv(btHeaders, btRows)) },
                { name: 'groups.csv', data: encoder.encode(objectsToCsv(groupHeaders, groupRows)) },
                { name: 'clusters.csv', data: encoder.encode(objectsToCsv(clusterHeaders, clusterRows)) },
                { name: 'hosts.csv', data: encoder.encode(objectsToCsv(hostHeaders, hostRows)) },
                { name: 'business_services.csv', data: encoder.encode(objectsToCsv(bsHeaders, bsRows)) },
                { name: 'relations.csv', data: encoder.encode(objectsToCsv(relHeaders, relRows)) },
                { name: 'sops.csv', data: encoder.encode(objectsToCsv(sopHeaders, sopRows)) },
                { name: 'whitelist.csv', data: encoder.encode(objectsToCsv(wlHeaders, wlRows)) },
            ]

            // Manifest
            const manifest = {
                version: 1,
                exportedAt: new Date().toISOString(),
                counts: {
                    clusterTypes: clusterTypes.length,
                    businessTypes: businessTypes.length,
                    groups: groups.length,
                    clusters: clusters.length,
                    hosts: allHosts.length,
                    businessServices: businessServices.length,
                    relations: relRows.length,
                    sops: sops.length,
                    whitelist: whitelistCommands.length,
                },
            }
            const manifestFile = {
                name: 'manifest.json',
                data: encoder.encode(JSON.stringify(manifest, null, 2)),
            }

            // Create ZIP and download
            const zipBlob = createZip([manifestFile, ...csvFiles])
            const url = URL.createObjectURL(zipBlob)
            const a = document.createElement('a')
            a.href = url
            a.download = `ops-resources-${new Date().toISOString().slice(0, 10)}.zip`
            a.click()
            URL.revokeObjectURL(url)
        } finally {
            setExporting(false)
        }
    }, [])

    return { exporting, exportAllAsZip }
}
