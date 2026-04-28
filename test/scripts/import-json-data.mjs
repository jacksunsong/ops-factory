/**
 * Clean up all system resource data, then import from a JSON or ZIP export file.
 *
 * Usage:  node import-json-data.mjs <path-to-file>
 *         node import-json-data.mjs              (uses default path)
 *
 * Supports:
 *   - JSON files (ops-resources-*.json)
 *   - ZIP files  (ops-resources-*.zip) containing CSV files
 *
 * Requires gateway running at http://127.0.0.1:3000
 */
import fs from 'fs'
import zlib from 'zlib'

const API = 'http://127.0.0.1:3000/gateway'
const H = { 'Content-Type': 'application/json', 'x-secret-key': 'test', 'x-user-id': 'admin' }
const filePath = process.argv[2] || 'C:\\Users\\sunsong\\Downloads\\ops-resources-2026-04-25 (5).zip'

// ── Helpers ────────────────────────────────────────────────────────────────

async function api(ep, method = 'GET', body = null) {
    const path = ep.startsWith('/') ? ep : `/${ep}`
    const opts = { method, headers: H }
    if (body) opts.body = JSON.stringify(body)
    const res = await fetch(`${API}${path}`, opts)
    return res.json()
}

async function getAll(ep) {
    const data = await api(ep)
    const key = Object.keys(data).find(k => Array.isArray(data[k]))
    return key ? data[key] : []
}

async function del(ep, id) { return api(`${ep}/${id}`, 'DELETE') }
async function create(ep, body) { return api(ep, 'POST', body) }
function log(msg) { console.log(`  ${msg}`) }

// ── Robust CSV parser (handles quoted multi-line fields) ──────────────────

function parseCsv(text) {
    // Strip BOM
    const s = text.replace(/^\uFEFF/, '')
    const records = []
    let i = 0

    function parseField() {
        if (i >= s.length) return ''
        if (s[i] === '"') {
            i++ // opening quote
            let field = ''
            while (i < s.length) {
                if (s[i] === '"') {
                    if (i + 1 < s.length && s[i + 1] === '"') { field += '"'; i += 2 }
                    else { i++; break } // closing quote
                } else { field += s[i]; i++ }
            }
            return field.trim()
        } else {
            let field = ''
            while (i < s.length && s[i] !== ',' && s[i] !== '\r' && s[i] !== '\n') {
                field += s[i]; i++
            }
            return field.trim()
        }
    }

    function parseRecord() {
        const fields = []
        fields.push(parseField())
        while (i < s.length && s[i] === ',') {
            i++ // skip comma
            fields.push(parseField())
        }
        // skip newline
        if (i < s.length && s[i] === '\r') i++
        if (i < s.length && s[i] === '\n') i++
        return fields
    }

    // Parse all records
    while (i < s.length) {
        // skip blank lines
        if (s[i] === '\r' || s[i] === '\n') { if (s[i] === '\r') i++; if (s[i] === '\n') i++; continue }
        records.push(parseRecord())
    }

    if (records.length < 2) return []
    const headers = records[0].map(h => h.toLowerCase())
    return records.slice(1)
        .filter(values => values.some(v => v))
        .map(values => {
            const obj = {}
            headers.forEach((h, idx) => { obj[h] = values[idx] ?? '' })
            return obj
        })
}

// ── ZIP reader ─────────────────────────────────────────────────────────────

function readZipCsv(zipPath, entryName) {
    const buf = fs.readFileSync(zipPath)
    let off = 0
    while (off < buf.length - 4) {
        if (buf.readUInt32LE(off) === 0x04034b50) {
            const nameLen = buf.readUInt16LE(off + 26)
            const name = buf.subarray(off + 30, off + 30 + nameLen).toString('utf8')
            const extraLen = buf.readUInt16LE(off + 28)
            const method = buf.readUInt16LE(off + 8)
            const compSize = buf.readUInt32LE(off + 18)
            if (name === entryName) {
                const data = buf.subarray(off + 30 + nameLen + extraLen, off + 30 + nameLen + extraLen + compSize)
                const text = method === 8 ? zlib.inflateRawSync(data).toString('utf8') : data.toString('utf8')
                return parseCsv(text)
            }
            off += 30 + nameLen + extraLen + compSize
        } else { off++ }
    }
    return null
}

// ── Clean Up ───────────────────────────────────────────────────────────────

async function cleanUp() {
    console.log('\n=== Cleaning up existing data ===\n')
    const cleanupOrder = [
        ['cluster-relations', 'cluster relations'],
        ['host-relations', 'host relations'],
        ['sops', 'SOPs'],
        ['command-whitelist', 'whitelist commands'],
        ['business-services', 'business services'],
        ['hosts', 'hosts'],
        ['clusters', 'clusters'],
    ]
    for (const [ep, label] of cleanupOrder) {
        const items = await getAll(ep)
        for (const item of items) await del(ep, item.id)
        log(`Deleted ${items.length} ${label}`)
    }
    // Groups: children first
    const groups = await getAll('host-groups')
    const sorted = [...groups].sort((a, b) => (a.parentId ? -1 : 0) - (b.parentId ? -1 : 0))
    for (const g of sorted) await del('host-groups', g.id)
    log(`Deleted ${groups.length} groups`)
    // Types
    for (const [ep, label] of [['business-types', 'business types'], ['cluster-types', 'cluster types']]) {
        const items = await getAll(ep)
        for (const item of items) await del(ep, item.id)
        log(`Deleted ${items.length} ${label}`)
    }
    console.log('\n✅ Cleanup complete.\n')
}

// ── Import from JSON ──────────────────────────────────────────────────────

async function importJson(jsonPath) {
    const data = JSON.parse(fs.readFileSync(jsonPath, 'utf8'))
    await doImport({
        clusterTypes: data.clusterTypes || [],
        businessTypes: data.businessTypes || [],
        groups: data.groups || [],
        clusters: data.clusters || [],
        hosts: data.hosts || [],
        relations: data.relations || [],
        sops: data.sops || [],
        whitelist: data.commandWhitelist || [],
    })
}

// ── Import from ZIP ───────────────────────────────────────────────────────

async function importZip(zipPath) {
    console.log(`Reading ZIP: ${zipPath}`)
    const clusterTypes = readZipCsv(zipPath, 'cluster_types.csv') || []
    const businessTypes = readZipCsv(zipPath, 'business_types.csv') || []
    const groups = readZipCsv(zipPath, 'groups.csv') || []
    const clusters = readZipCsv(zipPath, 'clusters.csv') || []
    const hosts = readZipCsv(zipPath, 'hosts.csv') || []
    const relations = readZipCsv(zipPath, 'relations.csv') || []
    const sops = readZipCsv(zipPath, 'sops.csv') || []
    const whitelist = readZipCsv(zipPath, 'whitelist.csv') || []

    console.log(`  CSV rows: ${clusterTypes.length} CT, ${businessTypes.length} BT, ${groups.length} groups, ${clusters.length} clusters, ${hosts.length} hosts, ${relations.length} relations, ${sops.length} SOPs, ${whitelist.length} WL\n`)

    await doImport({ clusterTypes, businessTypes, groups, clusters, hosts, relations, sops, whitelist })
}

// ── Common import logic ───────────────────────────────────────────────────

async function doImport({ clusterTypes, businessTypes, groups, clusters, hosts, relations, sops, whitelist }) {
    console.log('=== Importing data ===\n')

    const groupIdMap = new Map()
    const clusterIdMap = new Map()
    const hostIdMap = new Map()
    const clusterTypeNameToCode = new Map()

    // 1. Cluster Types
    let ok = 0
    for (const ct of clusterTypes) {
        try {
            const body = ct.name ? ct : { name: ct.name, code: ct.code, description: ct.description || '', knowledge: ct.knowledge || '', color: ct.color || '' }
            const res = await create('/cluster-types', body)
            if (res.clusterType?.id || res.id) { clusterTypeNameToCode.set(ct.name, ct.code); ok++ }
            else log(`⚠️ ClusterType "${ct.name}" failed: ${res.error || 'unknown'}`)
        } catch (e) { log(`⚠️ ClusterType "${ct.name}" error: ${e.message}`) }
    }
    log(`Created ${ok}/${clusterTypes.length} cluster types`)

    // 2. Business Types
    ok = 0
    for (const bt of businessTypes) {
        try {
            const res = await create('/business-types', { name: bt.name, code: bt.code, description: bt.description || '' })
            if (res.businessType?.id || res.id) ok++
        } catch (e) { /* skip */ }
    }
    log(`Created ${ok}/${businessTypes.length} business types`)

    // 3. Groups — root first, then children (multiple passes)
    // CSV parser lowercases headers, so fields are parentgroup not parentGroup
    const _parent = g => g.parentgroup || g.parentGroup || g.parentid || g.parentId || ''
    const rootGroups = groups.filter(g => !_parent(g))
    const childGroups = groups.filter(g => _parent(g))
    for (const g of rootGroups) {
        try {
            const res = await create('/host-groups', { name: g.name, code: g.code || '', description: g.description || '' })
            const newId = res.group?.id || res.id
            if (newId) groupIdMap.set(g.name, newId)
        } catch (e) { log(`⚠️ Group "${g.name}" error: ${e.message}`) }
    }
    log(`Created ${groupIdMap.size} root groups`)

    let remaining = [...childGroups]
    let passes = 10
    while (remaining.length > 0 && passes-- > 0) {
        const nextPass = []
        for (const g of remaining) {
            const parentName = g.parentgroup || g.parentGroup || g.parentid || g.parentId
            if (!groupIdMap.has(parentName)) { nextPass.push(g); continue }
            try {
                const res = await create('/host-groups', {
                    name: g.name, code: g.code || '',
                    parentId: groupIdMap.get(parentName),
                    description: g.description || '',
                })
                const newId = res.group?.id || res.id
                if (newId) groupIdMap.set(g.name, newId)
            } catch (e) { log(`⚠️ Group "${g.name}" error: ${e.message}`) }
        }
        if (nextPass.length === remaining.length) break
        remaining = nextPass
    }
    log(`Created ${groupIdMap.size} total groups`)

    // 4. Clusters
    ok = 0
    for (const c of clusters) {
        const groupName = c.group || c.groupid
        const mappedGroupId = groupName ? groupIdMap.get(groupName) : null
        try {
            const res = await create('/clusters', {
                name: c.name, type: c.type, purpose: c.purpose || '',
                groupId: mappedGroupId || null, description: c.description || '',
            })
            const newId = res.cluster?.id || res.id
            if (newId) { clusterIdMap.set(c.name, newId); ok++ }
        } catch (e) { log(`⚠️ Cluster "${c.name}" error: ${e.message}`) }
    }
    log(`Created ${ok}/${clusters.length} clusters`)

    // 5. Hosts
    ok = 0
    for (const h of hosts) {
        const clusterName = h.cluster || h.clusterid
        const mappedClusterId = clusterName ? clusterIdMap.get(clusterName) : null
        try {
            const res = await create('/hosts', {
                name: h.name, hostname: h.hostname || null,
                ip: h.ip || h.businessip || '', businessIp: h.businessip || null,
                port: h.port ? parseInt(h.port) : 22,
                os: h.os || null, location: h.location || null,
                username: h.username || 'aiuniagent',
                authType: h.authtype || h.authType || 'password', credential: '***',
                clusterId: mappedClusterId || null,
                purpose: h.purpose || null, business: h.business || null,
                description: h.description || '',
                tags: h.tags ? h.tags.split(';').map(t => t.trim()).filter(Boolean) : [],
                customAttributes: [],
            })
            const newId = res.host?.id || res.id
            if (newId) { hostIdMap.set(h.name, newId); ok++ }
        } catch (e) { /* skip */ }
    }
    log(`Created ${ok}/${hosts.length} hosts`)

    // 6. Relations
    ok = 0
    for (const r of relations) {
        const sourceName = r.sourcenode || r.sourceHostId
        const targetName = r.destnode || r.targetHostId
        const sourceId = hostIdMap.get(sourceName)
        const targetId = hostIdMap.get(targetName)
        if (!sourceId || !targetId) continue
        try {
            await create('/host-relations', {
                sourceHostId: sourceId, targetHostId: targetId,
                description: r.description || '',
            })
            ok++
        } catch (e) { /* skip */ }
    }
    log(`Created ${ok}/${relations.length} host relations`)

    // 7. Whitelist
    ok = 0
    for (const w of whitelist) {
        try {
            await create('/command-whitelist', { pattern: w.pattern, description: w.description || '', enabled: w.enabled !== 'false' })
            ok++
        } catch (e) { /* skip */ }
    }
    log(`Created ${ok}/${whitelist.length} whitelist commands`)

    // 8. SOPs
    ok = 0
    for (const sop of sops) {
        try {
            await create('/sops', {
                name: sop.name, description: sop.description || '',
                version: sop.version || '', triggerCondition: sop.triggercondition || sop.triggerCondition || '',
                enabled: sop.enabled !== 'false',
                mode: sop.mode || 'natural_language',
                stepsDescription: sop.stepsdescription || sop.stepsDescription || '',
                tags: sop.tags ? sop.tags.split(';').map(t => t.trim()).filter(Boolean) : [],
            })
            ok++
        } catch (e) { /* skip */ }
    }
    log(`Created ${ok}/${sops.length} SOPs`)

    console.log('\n✅ Import complete.\n')
}

// ── Main ───────────────────────────────────────────────────────────────────

try {
    await cleanUp()
    if (filePath.endsWith('.zip')) {
        await importZip(filePath)
    } else {
        await importJson(filePath)
    }
} catch (e) {
    console.error('Fatal error:', e)
    process.exit(1)
}
