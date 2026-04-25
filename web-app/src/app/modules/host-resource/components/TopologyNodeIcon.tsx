import type { CSSProperties, ReactNode, SVGProps } from 'react'

export type TopologyNodeKind = 'cluster' | 'host' | 'business'

type TopologyNodeIconProps = {
    kind: TopologyNodeKind
    size?: number
    title?: string
    className?: string
    style?: CSSProperties
}

type TopologyNodeSymbolOptions = {
    size?: number
    accentColor?: string
    surfaceColor?: string
    outlineColor?: string
    inkColor?: string
}

const DEFAULT_SURFACE = '#F8FAFC'
const DEFAULT_OUTLINE = '#CBD5E1'
const DEFAULT_INK = '#334155'

const DEFAULT_ACCENTS: Record<TopologyNodeKind, string> = {
    cluster: '#14B8A6',
    host: '#3B82F6',
    business: '#8B5CF6',
}

function frameProps(kind: TopologyNodeKind, title: string | undefined, size: number, className?: string, style?: CSSProperties): SVGProps<SVGSVGElement> {
    return {
        viewBox: '0 0 24 24',
        width: size,
        height: size,
        fill: 'none',
        className: ['hr-topology-icon', `hr-topology-icon--${kind}`, className].filter(Boolean).join(' '),
        style,
        role: title ? 'img' : 'presentation',
        'aria-hidden': title ? undefined : true,
        'aria-label': title,
    }
}

function IconFrame({ kind, children, size, title, className, style }: TopologyNodeIconProps & { children: ReactNode }) {
    return (
        <svg {...frameProps(kind, title, size ?? 24, className, style)}>
            <rect className="hr-topology-icon-frame" x="2.5" y="2.5" width="19" height="19" rx="5" />
            <path className="hr-topology-icon-link" d="M2.5 12H1.5m20 0h1" />
            {children}
        </svg>
    )
}

function ClusterIcon(props: TopologyNodeIconProps) {
    return (
        <IconFrame {...props}>
            <path className="hr-topology-icon-accent-fill" d="M12 7.1 16 9.4v5.2L12 16.9 8 14.6V9.4z" />
            <path className="hr-topology-icon-accent" d="M12 7.1 16 9.4v5.2L12 16.9 8 14.6V9.4z" />
            <path className="hr-topology-icon-accent" d="M12 7.1v4.9m4-2.6-4 2.6-4-2.6m4 2.6v4.9" />
            <circle className="hr-topology-icon-accent-solid" cx="12" cy="12" r="1" />
        </IconFrame>
    )
}

function HostIcon(props: TopologyNodeIconProps) {
    return (
        <IconFrame {...props}>
            <rect className="hr-topology-icon-accent-fill" x="7.1" y="7.6" width="9.8" height="6.3" rx="1.9" />
            <rect className="hr-topology-icon-accent" x="7.1" y="7.6" width="9.8" height="6.3" rx="1.9" />
            <path className="hr-topology-icon-accent" d="M9.3 16.5h5.4m-3.7-2.6v2.6m-1.8-5.9h.01m2.1 0h.01" />
            <path className="hr-topology-icon-ink" d="M12 13.9v1.2" />
        </IconFrame>
    )
}

function BusinessIcon(props: TopologyNodeIconProps) {
    return (
        <IconFrame {...props}>
            <rect className="hr-topology-icon-accent-fill" x="6.6" y="7.1" width="10.8" height="8.4" rx="2.1" />
            <rect className="hr-topology-icon-accent" x="6.6" y="7.1" width="10.8" height="8.4" rx="2.1" />
            <path className="hr-topology-icon-accent" d="M6.6 10h10.8M9.2 12.6h2.6m0 0 1.5-1.4m-1.5 1.4 1.5 1.4m-4.1 0h5.1" />
            <circle className="hr-topology-icon-accent-solid" cx="9" cy="8.6" r=".7" />
            <circle className="hr-topology-icon-accent-solid" cx="11" cy="8.6" r=".7" />
        </IconFrame>
    )
}

export default function TopologyNodeIcon(props: TopologyNodeIconProps) {
    switch (props.kind) {
        case 'cluster':
            return <ClusterIcon {...props} />
        case 'business':
            return <BusinessIcon {...props} />
        default:
            return <HostIcon {...props} />
    }
}

function withAlpha(hexColor: string, alpha = '1A'): string {
    if (!/^#([0-9a-fA-F]{6})$/.test(hexColor)) {
        return hexColor
    }
    return `${hexColor}${alpha}`
}

function buildIconSvg(kind: TopologyNodeKind, options: Required<TopologyNodeSymbolOptions>): string {
    const accentSoft = withAlpha(options.accentColor)
    const inner = kind === 'cluster'
        ? `
<path d="M12 7.1 16 9.4v5.2L12 16.9 8 14.6V9.4z" fill="${accentSoft}"/>
<path d="M12 7.1 16 9.4v5.2L12 16.9 8 14.6V9.4z" stroke="${options.accentColor}" stroke-width="1.4" stroke-linejoin="round"/>
<path d="M12 7.1v4.9m4-2.6-4 2.6-4-2.6m4 2.6v4.9" stroke="${options.accentColor}" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
<circle cx="12" cy="12" r="1" fill="${options.accentColor}"/>`
        : kind === 'business'
        ? `
<rect x="6.6" y="7.1" width="10.8" height="8.4" rx="2.1" fill="${accentSoft}"/>
<rect x="6.6" y="7.1" width="10.8" height="8.4" rx="2.1" stroke="${options.accentColor}" stroke-width="1.4"/>
<path d="M6.6 10h10.8M9.2 12.6h2.6m0 0 1.5-1.4m-1.5 1.4 1.5 1.4m-4.1 0h5.1" stroke="${options.accentColor}" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
<circle cx="9" cy="8.6" r=".7" fill="${options.accentColor}"/>
<circle cx="11" cy="8.6" r=".7" fill="${options.accentColor}"/>`
        : `
<rect x="7.1" y="7.6" width="9.8" height="6.3" rx="1.9" fill="${accentSoft}"/>
<rect x="7.1" y="7.6" width="9.8" height="6.3" rx="1.9" stroke="${options.accentColor}" stroke-width="1.4"/>
<path d="M9.3 16.5h5.4m-3.7-2.6v2.6m-1.8-5.9h.01m2.1 0h.01" stroke="${options.accentColor}" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M12 13.9v1.2" stroke="${options.inkColor}" stroke-width="1.4" stroke-linecap="round"/>`

    return `<svg width="${options.size}" height="${options.size}" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
<rect x="2.5" y="2.5" width="19" height="19" rx="5" fill="${options.surfaceColor}"/>
<rect x="2.5" y="2.5" width="19" height="19" rx="5" stroke="${options.outlineColor}" stroke-width="1.2"/>
<path d="M2.5 12H1.5m20 0h1" stroke="${options.outlineColor}" stroke-width="1.2" stroke-linecap="round"/>
${inner}
</svg>`
}

export function getTopologyNodeSymbol(kind: TopologyNodeKind, options: TopologyNodeSymbolOptions = {}): string {
    const normalized: Required<TopologyNodeSymbolOptions> = {
        size: options.size ?? 48,
        accentColor: options.accentColor ?? DEFAULT_ACCENTS[kind],
        surfaceColor: options.surfaceColor ?? DEFAULT_SURFACE,
        outlineColor: options.outlineColor ?? DEFAULT_OUTLINE,
        inkColor: options.inkColor ?? DEFAULT_INK,
    }
    return `image://data:image/svg+xml;charset=UTF-8,${encodeURIComponent(buildIconSvg(kind, normalized))}`
}

export const TOPOLOGY_ICON_DEFAULTS = {
    surface: DEFAULT_SURFACE,
    outline: DEFAULT_OUTLINE,
    ink: DEFAULT_INK,
    accents: DEFAULT_ACCENTS,
}
