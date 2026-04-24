/**
 * Minimal ZIP file creator — zero external dependencies.
 * Uses STORE mode (no compression), suitable for small-to-medium CSV payloads.
 */

const CRC_TABLE = (() => {
    const table = new Uint32Array(256)
    for (let i = 0; i < 256; i++) {
        let c = i
        for (let k = 0; k < 8; k++) {
            c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1)
        }
        table[i] = c
    }
    return table
})()

function crc32(data: Uint8Array): number {
    let crc = 0xFFFFFFFF
    for (let i = 0; i < data.length; i++) {
        crc = CRC_TABLE[(crc ^ data[i]) & 0xFF] ^ (crc >>> 8)
    }
    return (crc ^ 0xFFFFFFFF) >>> 0
}

export function createZip(files: { name: string; data: Uint8Array }[]): Blob {
    const encoder = new TextEncoder()

    // Encode file names and compute sizes
    const entries = files.map(f => {
        const nameBytes = encoder.encode(f.name)
        return { nameBytes, data: f.data, crc: crc32(f.data) }
    })

    // Calculate total size
    let localSize = 0
    for (const e of entries) {
        localSize += 30 + e.nameBytes.length + e.data.length
    }
    let centralSize = 0
    for (const e of entries) {
        centralSize += 46 + e.nameBytes.length
    }
    const endSize = 22
    const totalSize = localSize + centralSize + endSize

    const buf = new ArrayBuffer(totalSize)
    const view = new DataView(buf)
    const bytes = new Uint8Array(buf)
    let pos = 0
    const centralStart: number[] = []

    // Local file headers + data
    for (let i = 0; i < entries.length; i++) {
        const e = entries[i]
        centralStart.push(pos)

        view.setUint32(pos, 0x04034B50, true); pos += 4   // Local file header signature
        view.setUint16(pos, 20, true); pos += 2             // Version needed
        view.setUint16(pos, 0, true); pos += 2              // General purpose bit flag
        view.setUint16(pos, 0, true); pos += 2              // Compression method (STORE)
        view.setUint16(pos, 0, true); pos += 2              // Mod time
        view.setUint16(pos, 0, true); pos += 2              // Mod date
        view.setUint32(pos, e.crc, true); pos += 4          // CRC-32
        view.setUint32(pos, e.data.length, true); pos += 4  // Compressed size
        view.setUint32(pos, e.data.length, true); pos += 4  // Uncompressed size
        view.setUint16(pos, e.nameBytes.length, true); pos += 2 // File name length
        view.setUint16(pos, 0, true); pos += 2              // Extra field length
        bytes.set(e.nameBytes, pos); pos += e.nameBytes.length
        bytes.set(e.data, pos); pos += e.data.length
    }

    // Central directory
    const centralDirStart = pos
    for (let i = 0; i < entries.length; i++) {
        const e = entries[i]
        view.setUint32(pos, 0x02014B50, true); pos += 4    // Central dir signature
        view.setUint16(pos, 20, true); pos += 2             // Version made by
        view.setUint16(pos, 20, true); pos += 2             // Version needed
        view.setUint16(pos, 0, true); pos += 2              // Flags
        view.setUint16(pos, 0, true); pos += 2              // Compression (STORE)
        view.setUint16(pos, 0, true); pos += 2              // Mod time
        view.setUint16(pos, 0, true); pos += 2              // Mod date
        view.setUint32(pos, e.crc, true); pos += 4          // CRC-32
        view.setUint32(pos, e.data.length, true); pos += 4  // Compressed size
        view.setUint32(pos, e.data.length, true); pos += 4  // Uncompressed size
        view.setUint16(pos, e.nameBytes.length, true); pos += 2 // File name length
        view.setUint16(pos, 0, true); pos += 2              // Extra field length
        view.setUint16(pos, 0, true); pos += 2              // File comment length
        view.setUint16(pos, 0, true); pos += 2              // Disk number start
        view.setUint16(pos, 0, true); pos += 2              // Internal file attributes
        view.setUint32(pos, 0, true); pos += 4              // External file attributes
        view.setUint32(pos, centralStart[i], true); pos += 4 // Offset of local header
        bytes.set(e.nameBytes, pos); pos += e.nameBytes.length
    }

    // End of central directory
    view.setUint32(pos, 0x06054B50, true); pos += 4         // EOCD signature
    view.setUint16(pos, 0, true); pos += 2                  // Disk number
    view.setUint16(pos, 0, true); pos += 2                  // Disk with central dir
    view.setUint16(pos, entries.length, true); pos += 2     // Entries on this disk
    view.setUint16(pos, entries.length, true); pos += 2     // Total entries
    view.setUint32(pos, centralSize, true); pos += 4        // Central dir size
    view.setUint32(pos, centralDirStart, true); pos += 4    // Central dir offset
    view.setUint16(pos, 0, true); pos += 2                  // Comment length

    return new Blob([bytes], { type: 'application/zip' })
}
