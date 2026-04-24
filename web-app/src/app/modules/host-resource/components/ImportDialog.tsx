import { useState, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import DetailDialog from '../../../platform/ui/primitives/DetailDialog'
import type { ImportType, ImportResult, ImportProgress } from '../hooks/useResourceImport'

const IMPORT_TYPES: ImportType[] = [
    'ClusterTypes',
    'BusinessTypes',
    'HostGroups',
    'Clusters',
    'Hosts',
    'BusinessServices',
    'Relations',
    'SOPs',
    'Whitelist',
]

interface ImportDialogProps {
    open: boolean
    onClose: () => void
    importing: boolean
    progress: ImportProgress | null
    onImport: (type: ImportType, csvText: string) => Promise<ImportResult>
}

export default function ImportDialog({ open, onClose, importing, progress, onImport }: ImportDialogProps) {
    const { t } = useTranslation()
    const [selectedType, setSelectedType] = useState<ImportType | null>(null)
    const [selectedFile, setSelectedFile] = useState<File | null>(null)
    const [result, setResult] = useState<ImportResult | null>(null)
    const fileInputRef = useRef<HTMLInputElement>(null)

    if (!open) return null

    const handleTypeSelect = (type: ImportType) => {
        setSelectedType(type)
        setSelectedFile(null)
        setResult(null)
        if (fileInputRef.current) fileInputRef.current.value = ''
    }

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0]
        setSelectedFile(file ?? null)
        setResult(null)
    }

    const handleImport = async () => {
        if (!selectedType || !selectedFile) return
        const text = await selectedFile.text()
        const res = await onImport(selectedType, text)
        setResult(res)
    }

    const handleClose = () => {
        if (importing) return
        setSelectedType(null)
        setSelectedFile(null)
        setResult(null)
        if (fileInputRef.current) fileInputRef.current.value = ''
        onClose()
    }

    const handleContinue = () => {
        setSelectedType(null)
        setSelectedFile(null)
        setResult(null)
        if (fileInputRef.current) fileInputRef.current.value = ''
    }

    const typeLabel = (type: ImportType) => t(`hostResource.importType_${type}`)

    return (
        <DetailDialog
            title={t('hostResource.importDialogTitle')}
            onClose={handleClose}
            className="hr-import-dialog"
            bodyClassName="hr-import-dialog-body"
            footer={
                <div className="hr-import-dialog-footer">
                    {result ? (
                        <>
                            <button className="btn btn-secondary btn-sm" onClick={handleContinue}>
                                {t('hostResource.importContinue')}
                            </button>
                            <button className="btn btn-primary btn-sm" onClick={handleClose}>
                                {t('hostResource.importClose')}
                            </button>
                        </>
                    ) : (
                        <>
                            <button className="btn btn-secondary btn-sm" onClick={handleClose} disabled={importing}>
                                {t('hostResource.importClose')}
                            </button>
                            <button
                                className="btn btn-primary btn-sm"
                                onClick={handleImport}
                                disabled={!selectedType || !selectedFile || importing}
                            >
                                {importing ? t('hostResource.importing', { current: progress?.current ?? 0, total: progress?.total ?? 0 }) : t('hostResource.importStart')}
                            </button>
                        </>
                    )}
                </div>
            }
        >
            {/* Step 1: Select import type */}
            {!result && (
                <div className="hr-import-step">
                    <div className="hr-import-step-label">
                        {t('hostResource.importStep1')}
                    </div>
                    <div className="hr-import-type-grid">
                        {IMPORT_TYPES.map(type => (
                            <button
                                key={type}
                                type="button"
                                className={`hr-import-type-btn ${selectedType === type ? 'hr-import-type-btn-active' : ''}`}
                                onClick={() => handleTypeSelect(type)}
                                disabled={importing}
                            >
                                {typeLabel(type)}
                            </button>
                        ))}
                    </div>
                </div>
            )}

            {/* Step 2: Select CSV file */}
            {!result && selectedType && (
                <div className="hr-import-step">
                    <div className="hr-import-step-label">
                        {t('hostResource.importStep2')}
                    </div>
                    <div className="hr-import-file-area">
                        <input
                            ref={fileInputRef}
                            type="file"
                            accept=".csv"
                            onChange={handleFileChange}
                            disabled={importing}
                            className="hr-import-file-input"
                        />
                        {selectedFile && (
                            <div className="hr-import-file-name">{selectedFile.name}</div>
                        )}
                    </div>
                </div>
            )}

            {/* Progress */}
            {importing && progress && (
                <div className="hr-import-progress">
                    <div className="hr-import-progress-bar-track">
                        <div
                            className="hr-import-progress-bar-fill"
                            style={{ width: `${progress.total > 0 ? (progress.current / progress.total) * 100 : 0}%` }}
                        />
                    </div>
                    <div className="hr-import-progress-text">
                        {t('hostResource.importProgress', { current: progress.current, total: progress.total, phase: t(`hostResource.importType_${progress.phase}`) })}
                    </div>
                </div>
            )}

            {/* Result */}
            {result && (
                <div className="hr-import-result">
                    <div className="hr-import-result-summary">
                        {t('hostResource.importResultSummary', { success: result.success, failed: result.failed })}
                    </div>
                    {result.errors.length > 0 && (
                        <div className="hr-import-result-errors">
                            <div className="hr-import-result-errors-title">
                                {t('hostResource.importResultErrors')}
                            </div>
                            <ul className="hr-import-error-list">
                                {result.errors.map((err, idx) => (
                                    <li key={idx} className="hr-import-error-item">
                                        <span className="hr-import-error-row">Row {err.row}:</span> {err.message}
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}
                </div>
            )}
        </DetailDialog>
    )
}
