export interface Host {
    id: string
    name: string
    hostname?: string
    ip: string
    businessIp?: string
    port: number
    os?: string
    location?: string
    username: string
    authType: 'password' | 'key'
    credential?: string
    business?: string
    clusterId?: string
    purpose?: string
    tags: string[]
    description: string
    customAttributes?: CustomAttribute[]
    role?: 'primary' | 'backup' | null
    createdAt: string
    updatedAt: string
}

export interface CustomAttribute {
    key: string
    value: string
}

export interface HostCreateRequest {
    name: string
    hostname?: string
    ip: string
    businessIp?: string
    port: number
    os?: string
    location?: string
    username: string
    authType: 'password' | 'key'
    credential: string
    business?: string
    clusterId?: string
    purpose?: string
    tags: string[]
    description?: string
    customAttributes?: CustomAttribute[]
    role?: 'primary' | 'backup' | null
}

export interface HostTestResult {
    success: boolean
    message: string
    latency?: string
}

export interface HostGroup {
    id: string
    name: string
    code?: string
    parentId?: string | null
    description: string
    enabled?: boolean
    createdAt: string
    updatedAt: string
}

export interface Cluster {
    id: string
    name: string
    type: string
    purpose: string
    groupId?: string | null
    description: string
    createdAt: string
    updatedAt: string
}

export interface BusinessService {
    id: string
    name: string
    code: string
    groupId?: string | null
    businessTypeId?: string | null
    description: string
    hostIds: string[]
    contactInfo?: string
    tags: string[]
    priority: string
    createdAt: string
    updatedAt: string
}

export interface EnvVariable {
    key: string
    value: string
}

export interface ClusterType {
    id: string
    name: string
    code: string
    description: string
    color: string
    knowledge: string
    commandPrefix?: string
    envVariables?: EnvVariable[]
    mode?: 'peer' | 'primary-backup'
    createdAt: string
    updatedAt: string
}

export interface BusinessType {
    id: string
    name: string
    code: string
    description: string
    color: string
    knowledge: string
    createdAt: string
    updatedAt: string
}

export interface HostRelation {
    id: string
    sourceType?: 'host' | 'business-service'   // defaults to 'host'
    sourceHostId: string
    targetHostId: string
    description: string
    createdAt: string
    updatedAt: string
}

export interface GraphNode {
    id: string
    name: string
    ip: string | null
    businessIp?: string | null
    clusterType?: string | null
    clusterName?: string | null
    purpose?: string | null
    groupId?: string | null
    nodeType?: 'host' | 'business-service'
}

export interface GraphEdge {
    source: string
    target: string
    description: string
    type?: 'host-relation' | 'business-entry'
}

export interface GraphData {
    nodes: GraphNode[]
    edges: GraphEdge[]
}

export interface ClusterRelation {
    id: string
    sourceType: 'cluster' | 'business-service' | 'host'
    sourceId: string
    targetId: string
    description: string
    createdAt: string
    updatedAt: string
}

export interface ClusterGraphNode {
    id: string
    name: string
    type: string
    mode?: string
    groupId?: string | null
    hostCount: number
    nodeType: 'cluster' | 'business-service' | 'host'
    ip?: string | null          // host
    clusterId?: string | null   // host
    role?: string | null        // host
    clusterType?: string | null // host
}

export interface ClusterGraphEdge {
    source: string
    target: string
    description: string
    type?: 'cluster-relation' | 'business-entry' | 'constitute'
}

export interface ClusterGraphData {
    nodes: ClusterGraphNode[]
    edges: ClusterGraphEdge[]
}
