/**
 * CSV utilities for export/import of resource data.
 */

export function escapeCsvValue(value: unknown): string {
    const str = value == null ? '' : String(value)
    if (str.includes('"') || str.includes(',') || str.includes('\n') || str.includes('\r')) {
        return '"' + str.replace(/"/g, '""') + '"'
    }
    return str
}

/**
 * Convert an array of objects to a CSV string with UTF-8 BOM.
 * `headers` is an ordered list of { key, label? } pairs.
 */
export function objectsToCsv(
    headers: { key: string; label?: string }[],
    rows: Record<string, unknown>[],
): string {
    const headerLine = headers.map(h => escapeCsvValue(h.label ?? h.key)).join(',')
    const dataLines = rows.map(row =>
        headers.map(h => escapeCsvValue(row[h.key])).join(',')
    )
    return '\uFEFF' + headerLine + '\n' + dataLines.join('\n') + (dataLines.length > 0 ? '\n' : '')
}

/**
 * Parse a CSV string (with or without BOM) into an array of objects.
 * Uses the first row as headers.
 */
export function csvToObjects(csvText: string): Record<string, string>[] {
    // Strip BOM if present
    const text = csvText.replace(/^\uFEFF/, '')
    const lines = text.split(/\r?\n/).filter(line => line.trim())

    if (lines.length < 2) return []

    // Simple CSV parser respecting quoted fields
    function parseLine(line: string): string[] {
        const fields: string[] = []
        let current = ''
        let inQuotes = false
        for (let i = 0; i < line.length; i++) {
            const ch = line[i]
            if (inQuotes) {
                if (ch === '"') {
                    if (i + 1 < line.length && line[i + 1] === '"') {
                        current += '"'
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    current += ch
                }
            } else {
                if (ch === '"') {
                    inQuotes = true
                } else if (ch === ',') {
                    fields.push(current.trim())
                    current = ''
                } else {
                    current += ch
                }
            }
        }
        fields.push(current.trim())
        return fields
    }

    const headers = parseLine(lines[0]).map(h => h.toLowerCase())
    const result: Record<string, string>[] = []

    for (let i = 1; i < lines.length; i++) {
        const values = parseLine(lines[i])
        if (values.every(v => !v)) continue // skip blank rows
        const obj: Record<string, string> = {}
        headers.forEach((h, idx) => {
            obj[h] = values[idx] ?? ''
        })
        result.push(obj)
    }

    return result
}
