import { useState, useEffect, useCallback, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../../platform/ui/primitives/PageHeader'
import ListSearchInput from '../../../platform/ui/list/ListSearchInput'
import ListResultsMeta from '../../../platform/ui/list/ListResultsMeta'
import { useHostGroups } from '../hooks/useHostGroups'
import { useClusters } from '../hooks/useClusters'
import { useHostResource } from '../hooks/useHostResource'
import { useHostRelations } from '../hooks/useHostRelations'
import { useClusterRelations } from '../hooks/useClusterRelations'
import { useBusinessServices } from '../hooks/useBusinessServices'
import { useClusterTypes } from '../hooks/useClusterTypes'
import { useBusinessTypes } from '../hooks/useBusinessTypes'
import { useCommandWhitelist } from '../hooks/useCommandWhitelist'
import { useSops } from '../hooks/useSops'
import { useResourceExport } from '../hooks/useResourceExport'
import { useResourceImport } from '../hooks/useResourceImport'
import ResourceTree, { type TreeNode, type TreeNodeType } from '../components/ResourceTree'
import ResourceFormModal from '../components/ResourceFormModal'
import HostCard from '../components/HostCard'
import RelationGraph from '../components/RelationGraph'
import ClusterInsightPanel from '../components/ClusterInsightPanel'
import ClusterTypeTab from '../components/ClusterTypeTab'
import BusinessTypeTab from '../components/BusinessTypeTab'
import { SopsTab } from '../components/SopsTab'
import { WhitelistTab } from '../components/WhitelistTab'
import ImportDialog from '../components/ImportDialog'
import type { HostGroup, Cluster, Host, HostCreateRequest, BusinessService } from '../../../../types/host'
import '../styles/host-resource.css'

type SelectedNode = {
    id: string
    type: TreeNodeType
}

type EditingItem =
    | { type: 'group'; data: HostGroup }
    | { type: 'cluster'; data: Cluster }
    | { type: 'business-service'; data: BusinessService }
    | { type: 'host'; data: Host }
    | null

type TabKey = 'overview' | 'cluster-types' | 'business-types' | 'sop-management' | 'whitelist'

const PAGE_SIZE = 6

export default function HostResourcePage() {
    const { t } = useTranslation()
    const [activeTab, setActiveTab] = useState<TabKey>('overview')
    const [selected, setSelected] = useState<SelectedNode | null>(null)
    const [focusedHostId, setFocusedHostId] = useState<string | null>(null)
    const [selectedTopologyClusterId, setSelectedTopologyClusterId] = useState<string | null>(null)
    const [showModal, setShowModal] = useState(false)
    const [editingItem, setEditingItem] = useState<EditingItem>(null)
    const [currentPage, setCurrentPage] = useState(1)
    const [hostSearch, setHostSearch] = useState('')
    const [treeSearch, setTreeSearch] = useState('')
    const [showImportDialog, setShowImportDialog] = useState(false)
    const [testingId, setTestingId] = useState<string | null>(null)
    const [testResults, setTestResults] = useState<Record<string, { ok: boolean; msg: string }>>({})

    // Data hooks
    const { groups, fetchGroups, createGroup, updateGroup, deleteGroup } = useHostGroups()
    const { clusters, fetchAllClusters, createCluster, updateCluster, deleteCluster } = useClusters()
    const { hosts, allHosts, fetchHosts, fetchAllHosts, createHost, updateHost, deleteHost, testConnection } = useHostResource()
    const { relations: hostRelations, fetchGraph, fetchRelations: fetchHostRelations, createRelation } = useHostRelations()
    const { relations: clusterRelations, clusterGraphData, fetchClusterGraph, fetchRelations: fetchClusterRelations, createRelation: createClusterRelation, updateRelation: updateClusterRelation, deleteRelation: deleteClusterRelation } = useClusterRelations()
    const { businessServices, fetchBusinessServices, createBusinessService, updateBusinessService, deleteBusinessService } = useBusinessServices()
    const clusterTypesHook = useClusterTypes()
    const businessTypesHook = useBusinessTypes()
    const { commands: whitelistCommands, addCommand: addWhitelistCommand, fetchWhitelist: fetchWhitelistCommands } = useCommandWhitelist()
    const sopsHook = useSops()

    // Export / Import hooks
    const { exporting, exportAllAsZip } = useResourceExport()
    const { importing: csvImporting, progress: importProgress, importCsv } = useResourceImport({
        fetchGroups, fetchAllClusters, fetchAllHosts, fetchHostRelations, fetchBusinessServices, fetchGraph, fetchWhitelist: fetchWhitelistCommands,
        groups, clusters, allHosts, businessServices,
        clusterTypes: clusterTypesHook.clusterTypes,
        businessTypes: businessTypesHook.businessTypes,
        createGroup, updateGroup, createCluster, createHost,
        createBusinessService, createRelation,
        createClusterType: clusterTypesHook.createClusterType,
        createBusinessType: businessTypesHook.createBusinessType,
        createSop: sopsHook.createSop,
        addWhitelistCommand,
    })

    // Load all data on mount
    useEffect(() => { fetchGroups() }, [fetchGroups])
    useEffect(() => { fetchAllClusters() }, [fetchAllClusters])
    useEffect(() => { fetchAllHosts() }, [fetchAllHosts])
    useEffect(() => { fetchHostRelations() }, [fetchHostRelations])
    useEffect(() => { fetchBusinessServices() }, [fetchBusinessServices])

    // Resolve the province-level group ID for a business service by walking up the tree
    // until we find a group whose parent is a root group (no parentId).
    // This works for any nesting depth: 1-level, 2-level, 3-level, etc.
    const resolveProvinceGroupId = useCallback((bs: BusinessService): string | undefined => {
        let current = groups.find(g => g.id === bs.groupId)
        if (!current) return undefined

        // Walk up until we find a group whose parent is a root group
        while (current?.parentId) {
            const parent = groups.find(g => g.id === current!.parentId)
            if (!parent?.parentId) {
                // current's parent is a root group → current is the province-level group
                return current.id
            }
            current = parent
        }

        // BS is in a root group or group with no parent → return its own group ID
        return current?.id
    }, [groups])

    // Refresh host list respecting the current tree selection filter
    const refreshHostList = useCallback(() => {
        if (selected?.type === 'cluster') {
            fetchHosts(selected.id, undefined)
        } else if (selected?.type === 'business-service') {
            fetchHosts(undefined, undefined, selected.id)
        } else if (selected?.type === 'group' || selected?.type === 'subgroup') {
            fetchHosts(undefined, selected.id)
        } else {
            fetchHosts()
        }
    }, [selected, fetchHosts])

    // Fetch hosts based on tree selection
    useEffect(() => {
        refreshHostList()
    }, [refreshHostList])

    // Fetch graph based on tree selection
    useEffect(() => {
        if (selected?.type === 'cluster') {
            fetchClusterGraph()
            setFocusedHostId(null)
            setSelectedTopologyClusterId(selected.id)
        } else if (selected?.type === 'business-service') {
            const bs = businessServices.find(b => b.id === selected.id)
            if (bs) {
                const provinceId = resolveProvinceGroupId(bs)
                fetchClusterGraph(provinceId)
            } else {
                fetchClusterGraph()
            }
            setFocusedHostId(selected.id)
            setSelectedTopologyClusterId(null)
        } else if (selected?.type === 'group' || selected?.type === 'subgroup') {
            fetchClusterGraph(selected.id)
            setFocusedHostId(null)
            setSelectedTopologyClusterId(null)
        } else {
            fetchClusterGraph()
            setFocusedHostId(null)
            setSelectedTopologyClusterId(null)
        }
    }, [selected, fetchClusterGraph, businessServices, resolveProvinceGroupId])

    const topologyNodeMap = useMemo(() => {
        const map = new Map(clusterGraphData.nodes.map(node => [node.id, node]))
        return map
    }, [clusterGraphData])

    const selectedTopologyCluster = useMemo(() => {
        if (!selectedTopologyClusterId) return null
        const clusterFromList = clusters.find(cluster => cluster.id === selectedTopologyClusterId)
        if (clusterFromList) return clusterFromList

        const graphNode = topologyNodeMap.get(selectedTopologyClusterId)
        if (graphNode?.nodeType !== 'cluster') return null

        return {
            id: graphNode.id,
            name: graphNode.name,
            type: graphNode.type ?? graphNode.clusterType ?? '',
            purpose: '',
            description: '',
            groupId: graphNode.groupId ?? null,
            createdAt: '',
            updatedAt: '',
        } satisfies Cluster
    }, [selectedTopologyClusterId, clusters, topologyNodeMap])

    const selectedTopologyClusterHosts = useMemo(() => {
        if (!selectedTopologyClusterId) return []
        return allHosts.filter(host => host.clusterId === selectedTopologyClusterId)
    }, [allHosts, selectedTopologyClusterId])

    const topologyGraphData = useMemo(() => {
        const visibleNodeIds = new Set(
            clusterGraphData.nodes
                .filter(node => node.nodeType === 'cluster' || node.nodeType === 'business-service')
                .map(node => node.id),
        )

        return {
            nodes: clusterGraphData.nodes.filter(node => visibleNodeIds.has(node.id)),
            edges: clusterGraphData.edges.filter(edge =>
                edge.type !== 'constitute'
                && visibleNodeIds.has(edge.source)
                && visibleNodeIds.has(edge.target),
            ),
        }
    }, [clusterGraphData])

    // Build tree data — recursive, supports arbitrary nesting depth
    const treeData = useMemo((): TreeNode[] => {
        const clusterHostMap = new Map<string, number>()
        for (const h of allHosts) {
            if (h.clusterId) {
                clusterHostMap.set(h.clusterId, (clusterHostMap.get(h.clusterId) || 0) + 1)
            }
        }

        // Index children by parentId
        const childrenMap = new Map<string, HostGroup[]>()
        for (const g of groups) {
            if (g.parentId) {
                const list = childrenMap.get(g.parentId) || []
                list.push(g)
                childrenMap.set(g.parentId, list)
            }
        }

        const buildGroupNode = (g: HostGroup): TreeNode => {
            const childGroups = childrenMap.get(g.id) || []
            const childNodes: TreeNode[] = []

            // Recursively build child group nodes first
            for (const cg of childGroups) {
                childNodes.push(buildGroupNode(cg))
            }

            // Business services under this group
            const groupBs = businessServices.filter(bs => bs.groupId === g.id)
            for (const bs of groupBs) {
                const hostNames = bs.hostIds
                    .map(hid => allHosts.find(h => h.id === hid)?.name)
                    .filter(Boolean)
                    .join(', ')
                childNodes.push({
                    id: bs.id,
                    type: 'business-service' as TreeNodeType,
                    name: bs.name,
                    subtitle: hostNames || bs.code,
                    raw: bs,
                })
            }

            // Clusters under this group
            const groupClusters = clusters.filter(c => c.groupId === g.id)
            for (const c of groupClusters) {
                childNodes.push({
                    id: c.id,
                    type: 'cluster' as TreeNodeType,
                    name: c.name,
                    subtitle: c.type + (clusterHostMap.has(c.id) ? ` (${clusterHostMap.get(c.id)} ${t('hostResource.hostCountUnit')})` : ''),
                    raw: c,
                })
            }

            return {
                id: g.id,
                type: 'subgroup' as TreeNodeType,
                name: g.name,
                subtitle: g.code || undefined,
                children: childNodes.length > 0 ? childNodes : undefined,
                raw: g,
            }
        }

        // Root groups (no parentId) become top-level tree nodes
        const rootGroups = groups.filter(g => !g.parentId)
        const tree = rootGroups.map(g => {
            const node = buildGroupNode(g)
            return { ...node, type: 'group' as TreeNodeType }
        })

        // Mark inheritedDisabled: a node is visually disabled if it or any ancestor group has enabled=false
        function markInherited(nodes: TreeNode[], ancestorOff: boolean) {
            for (const n of nodes) {
                const isGroup = n.type === 'group' || n.type === 'subgroup'
                const groupEnabled = isGroup && n.raw ? (n.raw as HostGroup).enabled !== false : true
                const effective = ancestorOff || !groupEnabled
                n.inheritedDisabled = effective
                if (n.children) markInherited(n.children, effective)
            }
        }
        markInherited(tree, false)

        return tree
    }, [groups, clusters, allHosts, businessServices, t])

    // Build cluster lookup for HostCard
    const clusterMap = useMemo(() => {
        const map = new Map<string, Cluster>()
        for (const c of clusters) map.set(c.id, c)
        return map
    }, [clusters])

    const handleSelect = useCallback((id: string, type: TreeNodeType) => {
        setSelected(prev => prev?.id === id && prev?.type === type ? prev : { id, type })
        setFocusedHostId(null)
        setCurrentPage(1)
    }, [])

    const handleTreeEdit = useCallback((id: string, type: TreeNodeType) => {
        if (type === 'group' || type === 'subgroup') {
            const g = groups.find(g => g.id === id)
            if (g) {
                setEditingItem({ type: 'group', data: g })
                setShowModal(true)
            }
        } else if (type === 'business-service') {
            const bs = businessServices.find(b => b.id === id)
            if (bs) {
                setEditingItem({ type: 'business-service', data: bs })
                setShowModal(true)
            }
        } else if (type === 'cluster') {
            const c = clusters.find(c => c.id === id)
            if (c) {
                setEditingItem({ type: 'cluster', data: c })
                setShowModal(true)
            }
        }
    }, [groups, clusters, businessServices])

    const handleTreeDelete = useCallback(async (id: string, type: TreeNodeType) => {
        if (type === 'group' || type === 'subgroup') {
            if (confirm(t('hostResource.confirmDeleteGroup'))) {
                try {
                    await deleteGroup(id)
                    if (selected?.id === id) setSelected(null)
                } catch (err) {
                    if ((err as any)?.status === 409 && confirm(t('hostResource.confirmForceDeleteGroup'))) {
                        try {
                            await deleteGroup(id, true)
                            if (selected?.id === id) setSelected(null)
                        } catch (err2) {
                            alert(err2 instanceof Error ? err2.message : 'Failed')
                        }
                    } else if ((err as any)?.status !== 409) {
                        alert(err instanceof Error ? err.message : 'Failed')
                    }
                }
            }
        } else if (type === 'business-service') {
            if (confirm(t('hostResource.confirmDeleteBusinessService'))) {
                try {
                    await deleteBusinessService(id)
                    if (selected?.id === id) setSelected(null)
                } catch (err) {
                    alert(err instanceof Error ? err.message : 'Failed')
                }
            }
        } else if (type === 'cluster') {
            if (confirm(t('hostResource.confirmDeleteCluster'))) {
                try {
                    await deleteCluster(id)
                    if (selected?.id === id) setSelected(null)
                } catch (err) {
                    if ((err as any)?.status === 409 && confirm(t('hostResource.confirmForceDeleteCluster'))) {
                        try {
                            await deleteCluster(id, true)
                            if (selected?.id === id) setSelected(null)
                        } catch (err2) {
                            alert(err2 instanceof Error ? err2.message : 'Failed')
                        }
                    } else if ((err as any)?.status !== 409) {
                        alert(err instanceof Error ? err.message : 'Failed')
                    }
                }
            }
        }
    }, [deleteGroup, deleteCluster, deleteBusinessService, selected, t])

    const handleDeleteHost = useCallback(async (host: Host) => {
        if (confirm(t('hostResource.confirmDeleteHost'))) {
            try {
                await deleteHost(host.id)
                if (focusedHostId === host.id) setFocusedHostId(null)
                refreshHostList()
            } catch (err) {
                alert(err instanceof Error ? err.message : 'Failed')
            }
        }
    }, [deleteHost, focusedHostId, t, refreshHostList])

    const handleTestHost = useCallback(async (host: Host) => {
        setTestingId(host.id)
        setTestResults(prev => {
            const next = { ...prev }
            delete next[host.id]
            return next
        })
        try {
            const result = await testConnection(host.id)
            if (result?.success) {
                const msg = t('remoteDiagnosis.hosts.testSuccess', { latency: `${result.latency ?? ''}ms` })
                setTestResults(prev => ({ ...prev, [host.id]: { ok: true, msg } }))
            } else {
                const msg = t('remoteDiagnosis.hosts.testFailed', { error: result?.message || 'Unknown' })
                setTestResults(prev => ({ ...prev, [host.id]: { ok: false, msg } }))
            }
        } catch (err) {
            const msg = t('remoteDiagnosis.hosts.testFailed', { error: err instanceof Error ? err.message : 'Unknown' })
            setTestResults(prev => ({ ...prev, [host.id]: { ok: false, msg } }))
        } finally {
            setTestingId(null)
        }
    }, [testConnection, t])

    const handleHostCardClick = useCallback((host: Host) => {
        setFocusedHostId(prev => prev === host.id ? null : host.id)
    }, [])

    const defaultGroupIdForCreate = selected?.type === 'group' || selected?.type === 'subgroup' ? selected.id : undefined
    const defaultClusterIdForCreate = selected?.type === 'cluster' ? selected.id : undefined

    // Client-side tree search filter
    const filteredTreeData = useMemo(() => {
        if (!treeSearch.trim()) return treeData
        const term = treeSearch.toLowerCase()
        function filterNodes(nodes: TreeNode[]): TreeNode[] {
            return nodes.reduce<TreeNode[]>((acc, node) => {
                const childMatch = node.children ? filterNodes(node.children) : []
                const selfMatch = node.name.toLowerCase().includes(term)
                    || (node.subtitle && node.subtitle.toLowerCase().includes(term))
                if (selfMatch || childMatch.length > 0) {
                    acc.push({ ...node, children: childMatch.length > 0 ? childMatch : node.children })
                }
                return acc
            }, [])
        }
        return filterNodes(treeData)
    }, [treeData, treeSearch])

    // Client-side search filter (default alphabetical ascending by name)
    const filteredHosts = useMemo(() => {
        const list = hostSearch.trim()
            ? hosts.filter(h => h.name.toLowerCase().includes(hostSearch.toLowerCase()))
            : hosts
        return [...list].sort((a, b) => a.name.localeCompare(b.name))
    }, [hosts, hostSearch])

    // Pagination
    const totalPages = Math.max(1, Math.ceil(filteredHosts.length / PAGE_SIZE))
    const safePage = Math.min(currentPage, totalPages)
    const paginatedHosts = filteredHosts.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE)

    const handleExport = useCallback(() => {
        exportAllAsZip({
            groups, clusters, allHosts, hostRelations,
            businessServices,
            clusterTypes: clusterTypesHook.clusterTypes,
            businessTypes: businessTypesHook.businessTypes,
            whitelistCommands,
            sops: sopsHook.sops,
        })
    }, [exportAllAsZip, groups, clusters, allHosts, hostRelations, businessServices, clusterTypesHook.clusterTypes, businessTypesHook.businessTypes, whitelistCommands, sopsHook.sops])

    const openCreateModal = useCallback(() => {
        setEditingItem(null)
        setShowModal(true)
    }, [])

    const openEditModal = useCallback((item: EditingItem) => {
        setEditingItem(item)
        setShowModal(true)
    }, [])

    const tabs: { key: TabKey; label: string }[] = [
        { key: 'overview', label: t('hostResource.tabOverview') },
        { key: 'cluster-types', label: t('hostResource.tabClusterTypes') },
        { key: 'business-types', label: t('hostResource.tabBusinessTypes') },
        { key: 'sop-management', label: t('hostResource.tabSopManagement') },
        { key: 'whitelist', label: t('hostResource.tabWhitelist') },
    ]

    return (
        <div className="page-container sidebar-top-page host-resource-page">
            <PageHeader
                title={t('sidebar.hostResource')}
                action={
                    <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--spacing-2, 8px)' }}>
                        {activeTab === 'overview' && (
                            <button className="btn btn-primary btn-sm" onClick={openCreateModal}>
                                + {t('hostResource.createResource')}
                            </button>
                        )}
                        <button className="btn btn-secondary btn-sm" onClick={handleExport} disabled={exporting}>
                            {exporting ? t('hostResource.exporting') : t('hostResource.export')}
                        </button>
                        <button className="btn btn-secondary btn-sm" onClick={() => setShowImportDialog(true)} disabled={csvImporting}>
                            {t('hostResource.import')}
                        </button>
                    </div>
                }
            />

            {/* Tab Navigation */}
            <div className="config-tabs">
                {tabs.map(tab => (
                    <button
                        key={tab.key}
                        type="button"
                        className={`config-tab ${activeTab === tab.key ? 'config-tab-active' : ''}`}
                        onClick={() => setActiveTab(tab.key)}
                    >
                        {tab.label}
                    </button>
                ))}
            </div>

            {/* Tab content */}
            {activeTab === 'overview' && (
                <>

                    <div className="hr-layout-main">
                        {/* Left: Resource Tree */}
                        <div className="hr-tree-sidebar">
                            <div className="hr-tree-search">
                                <ListSearchInput
                                    value={treeSearch}
                                    placeholder={t('hostResource.searchTree')}
                                    onChange={setTreeSearch}
                                />
                            </div>
                            <ResourceTree
                                tree={filteredTreeData}
                                selectedId={selected?.id ?? null}
                                selectedType={selected?.type ?? null}
                                onSelect={handleSelect}
                                onEdit={handleTreeEdit}
                                onDelete={handleTreeDelete}
                            />
                        </div>

                        {/* Right: Host Cards */}
                        <div className="hr-cards-area">
                            {hosts.length === 0 ? (
                                <div className="hr-empty">{t('hostResource.noHosts')}</div>
                            ) : (
                                <>
                                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--spacing-3)', marginBottom: 'var(--spacing-3)' }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--spacing-2, 8px)', flex: 1 }}>
                                            <ListSearchInput
                                                value={hostSearch}
                                                placeholder={t('hostResource.searchHosts')}
                                                onChange={setHostSearch}
                                            />
                                            {hostSearch && (
                                                <ListResultsMeta>
                                                    {t('common.resultsFound', { count: filteredHosts.length })}
                                                </ListResultsMeta>
                                            )}
                                        </div>
                                        {selected?.type === 'cluster' && (
                                            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--spacing-2, 8px)', flexShrink: 0 }}>
                                            </div>
                                        )}
                                    </div>
                                    <div className="hr-host-grid">
                                        {paginatedHosts.map(host => (
                                            <HostCard
                                                key={host.id}
                                                host={host}
                                                cluster={host.clusterId ? clusterMap.get(host.clusterId) : undefined}
                                                selected={focusedHostId === host.id}
                                                testing={testingId === host.id}
                                                testResult={testResults[host.id] ?? null}
                                                onClick={() => handleHostCardClick(host)}
                                                onEdit={() => openEditModal({ type: 'host', data: host })}
                                                onDelete={() => handleDeleteHost(host)}
                                                onTest={() => handleTestHost(host)}
                                            />
                                        ))}
                                    </div>
                                    {totalPages > 1 && (
                                        <div className="hr-pagination">
                                            <span className="hr-pagination-info">
                                                {t('common.showing', {
                                                    start: (safePage - 1) * PAGE_SIZE + 1,
                                                    end: Math.min(safePage * PAGE_SIZE, filteredHosts.length),
                                                    total: filteredHosts.length,
                                                })}
                                            </span>
                                            <div className="hr-pagination-controls">
                                                <button
                                                    className="hr-pagination-btn"
                                                    disabled={safePage <= 1}
                                                    onClick={() => setCurrentPage(safePage - 1)}
                                                >
                                                    {t('common.previousPage')}
                                                </button>
                                                <span className="hr-pagination-page">{safePage} / {totalPages}</span>
                                                <button
                                                    className="hr-pagination-btn"
                                                    disabled={safePage >= totalPages}
                                                    onClick={() => setCurrentPage(safePage + 1)}
                                                >
                                                    {t('common.nextPage')}
                                                </button>
                                            </div>
                                        </div>
                                    )}
                                </>
                            )}
                        </div>
                    </div>

                    {/* Bottom: Topology */}
                    <div className="hr-topology-area">
                        <div className="hr-topology-workbench">
                            <div className="hr-topology-graph-pane">
                                <div className="hr-topology-pane-header">
                                    <div>
                                        <h3 className="hr-topology-pane-title">{t('hostResource.clusterRelationsTitle')}</h3>
                                        <p className="hr-topology-pane-subtitle">{t('hostResource.clusterRelationsSubtitle')}</p>
                                    </div>
                                </div>
                                <RelationGraph
                                    data={topologyGraphData}
                                    focusedHostId={focusedHostId}
                                    onNodeClick={(nodeId) => {
                                        const node = topologyNodeMap.get(nodeId)
                                        if (node?.nodeType === 'cluster') {
                                            setSelectedTopologyClusterId(prev => prev === nodeId ? null : nodeId)
                                            setFocusedHostId(null)
                                            return
                                        }
                                        setFocusedHostId(prev => prev === nodeId ? null : nodeId)
                                    }}
                                    onBackgroundClick={() => {
                                        setFocusedHostId(null)
                                    }}
                                />
                            </div>
                            <ClusterInsightPanel
                                cluster={selectedTopologyCluster}
                                hosts={selectedTopologyClusterHosts}
                                graphData={clusterGraphData}
                                viewingClusterHosts={selected?.type === 'cluster' && selected.id === selectedTopologyCluster?.id}
                                onViewClusterHosts={(clusterId) => {
                                    setSelected({ id: clusterId, type: 'cluster' })
                                    setFocusedHostId(null)
                                    setCurrentPage(1)
                                }}
                            />
                        </div>
                    </div>
                </>
            )}

            {activeTab === 'cluster-types' && (
                <ClusterTypeTab
                    clusterTypes={clusterTypesHook.clusterTypes}
                    loading={clusterTypesHook.loading}
                    onCreate={clusterTypesHook.createClusterType}
                    onUpdate={clusterTypesHook.updateClusterType}
                    onDelete={clusterTypesHook.deleteClusterType}
                />
            )}

            {activeTab === 'business-types' && (
                <BusinessTypeTab
                    businessTypes={businessTypesHook.businessTypes}
                    loading={businessTypesHook.loading}
                    onCreate={businessTypesHook.createBusinessType}
                    onUpdate={businessTypesHook.updateBusinessType}
                    onDelete={businessTypesHook.deleteBusinessType}
                />
            )}

            {activeTab === 'sop-management' && <SopsTab />}

            {activeTab === 'whitelist' && <WhitelistTab />}

            {/* Create/Edit Modal */}
            {showModal && (
                <ResourceFormModal
                    key={editingItem?.type === 'business-service' ? `bs-${editingItem.data.id}` : editingItem?.type === 'cluster' ? `cl-${editingItem.data.id}` : editingItem?.type === 'group' ? `gr-${editingItem.data.id}` : editingItem?.type === 'host' ? `h-${editingItem.data.id}` : 'create'}
                    editingItem={editingItem}
                    groups={groups}
                    clusters={clusters}
                    hosts={allHosts}
                    defaultGroupId={defaultGroupIdForCreate}
                    defaultClusterId={defaultClusterIdForCreate}
                    clusterTypes={clusterTypesHook.clusterTypes}
                    businessTypes={businessTypesHook.businessTypes}
                    businessServices={businessServices}
                    clusterRelations={clusterRelations}
                    fetchClusterRelations={fetchClusterRelations}
                    onClose={() => { setShowModal(false); setEditingItem(null) }}
                    onSaveGroup={async (data) => {
                        if (editingItem?.type === 'group') {
                            await updateGroup(editingItem.data.id, data)
                        } else {
                            await createGroup(data)
                        }
                    }}
                    onSaveCluster={async (data) => {
                        if (editingItem?.type === 'cluster') {
                            await updateCluster(editingItem.data.id, data)
                        } else {
                            await createCluster(data)
                        }
                    }}
                    onSaveBusinessService={async (data) => {
                        if (editingItem?.type === 'business-service') {
                            await updateBusinessService(editingItem.data.id, data)
                        } else {
                            await createBusinessService(data)
                        }
                    }}
                    onSaveHost={async (data) => {
                        if (editingItem?.type === 'host') {
                            await updateHost(editingItem.data.id, data as Partial<Host>)
                        } else {
                            await createHost(data as unknown as HostCreateRequest)
                        }
                        refreshHostList()
                    }}
                    onSaveClusterRelation={createClusterRelation}
                    onUpdateClusterRelation={updateClusterRelation}
                    onDeleteClusterRelation={deleteClusterRelation}
                />
            )}

            {/* Import Dialog */}
            <ImportDialog
                open={showImportDialog}
                onClose={() => setShowImportDialog(false)}
                importing={csvImporting}
                progress={importProgress}
                onImport={importCsv}
            />
        </div>
    )
}
