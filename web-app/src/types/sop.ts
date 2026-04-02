export interface SopNode {
    id: string
    name: string
    type: 'start' | 'analysis'
    hostTags: string[]
    command: string
    variables: SopVariable[]
    commandVariables?: Record<string, SopCommandVariable>
    outputFormat: string
    analysisInstruction: string
    transitions: SopTransition[]
}

export interface SopVariable {
    name: string
    description?: string
    defaultValue?: string
    required?: boolean
}

export interface SopCommandVariable {
    description: string
    defaultValue: string
    required: boolean
}

export interface SopTransition {
    condition: string
    description?: string
    nextNodeId?: string
    nextNodes?: string[]
}

export interface Sop {
    id: string
    name: string
    description: string
    version: string
    triggerCondition: string
    nodes: SopNode[]
}

export interface SopCreateRequest {
    name: string
    description?: string
    version?: string
    triggerCondition?: string
    nodes?: SopNode[]
}
