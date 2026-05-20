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

// Sample CSV content for each import type
const SAMPLE_CSVS: Record<ImportType, string> = {
    ClusterTypes: `name,code,description,knowledge,commandPrefix,envVariables
Web Cluster,web-cluster,Web service cluster,Common commands: nginx,tomcat,/usr/bin/,JAVA_HOME=/opt/java;NODE_ENV=production
Database Cluster,db-cluster,Database service cluster,Common commands: mysql,postgres,/usr/bin/,DB_PORT=3306`,
    BusinessTypes: `name,code,description,knowledge
Payment Service,payment,Payment related services,Standard payment flow
User Service,user,User management services,User registration, login, authentication`,
    HostGroups: `name,code,parentGroup,description
Production Environment,prod,,Production servers
Production-Web,web,Production Environment,Production Web servers
Production-DB,db,Production Environment,Production database servers`,
    Clusters: `name,type,purpose,group,description
Web01,Web Cluster,Frontend services,Production-Web,Web frontend server cluster
DB01,Database Cluster,Data storage,Production-DB,MySQL database cluster`,
    Hosts: `name,hostname,ip,businessIp,port,os,location,username,authType,credential,business,cluster,purpose,tags,description
web-server-01,web01,192.168.1.10,,22,Linux,Beijing Datacenter,root,password,,Web01,Web Server,web;prod,Primary Web Server
web-server-02,web02,192.168.1.11,,22,Linux,Beijing Datacenter,root,password,,Web01,Web Server,web;prod,Backup Web Server`,
    BusinessServices: `name,code,group,businessType,description,tags,priority,contactInfo
Payment Service,payment-svc,Production Environment,Payment Service,Online payment interface,payment;prod,High,Ops Team
User Service,user-svc,Production Environment,User Service,User management features,user;prod,Medium,Dev Team`,
    Relations: `sourceNode,destNode,description
web-server-01,db-server-01,Web server accessing database
web-server-02,db-server-01,Backup web server accessing database`,
    SOPs: `name,description,version,triggerCondition,enabled,mode,stepsDescription,tags
Server Restart,Regularly restart servers to free resources,v1.0,Memory usage over 90%,true,structured,1.Check current memory usage;2.Notify相关人员;3.Execute restart;4.Verify service recovery,restart;ops
Log Cleanup,Regularly clean up log files,v1.1,Disk usage over 80%,true,structured,1.Check log directory;2.Delete logs older than 7 days;3.Verify disk space,cleanup;ops`,
    Whitelist: `pattern,description,enabled
ls -la,List files,true
ps aux,View processes,true
cat /var/log/*,View logs,false`,
}

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
        // Read file as text with UTF-8 encoding
        // Note: For non-UTF-8 CSV files (like GBK), please convert them to UTF-8 first
        // using a tool like Notepad++ or VSCode (File -> Save with Encoding -> UTF-8)
        const text = await selectedFile.text()
        const res = await onImport(selectedType, text)
        setResult(res)
    }

    const downloadSample = () => {
        if (!selectedType) return
        const csv = SAMPLE_CSVS[selectedType]
        const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `${selectedType.toLowerCase()}_sample.csv`
        a.click()
        URL.revokeObjectURL(url)
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

    const translateError = (err: ImportResult['errors'][0]): string => {
        switch (err.code) {
            case 'import.noDataRows':
                return t('hostResource.importErrorNoDataRows')
            case 'import.missingRequiredColumns':
                return t('hostResource.importErrorMissingRequiredColumns', {
                    type: err.params?.type,
                    columns: err.params?.columns,
                })
            case 'import.wrongFileType.suggestBusinessTypes':
                return t('hostResource.importErrorSuggestBusinessTypes')
            case 'import.wrongFileType.suggestClusterTypes':
                return t('hostResource.importErrorSuggestClusterTypes')
            case 'import.wrongFileType.suggestOther':
                return t('hostResource.importErrorSuggestOther', { types: err.params?.types })
            case 'import.groupNotFound':
                return t('hostResource.importErrorGroupNotFound', { group: err.params?.group })
            case 'import.clusterNotFound':
                return t('hostResource.importErrorClusterNotFound', { cluster: err.params?.cluster })
            case 'import.targetHostNotFound':
                return t('hostResource.importErrorTargetHostNotFound', { host: err.params?.host })
            case 'import.sourceNodeNotFound':
                return t('hostResource.importErrorSourceNodeNotFound', { node: err.params?.node })
            case 'import.rowError':
                return t('hostResource.importErrorRowError', { message: err.params?.message })
            case 'import.setParentFailed':
                return t('hostResource.importErrorSetParentFailed', { message: err.params?.message })
            case 'import.importFailed':
                return t('hostResource.importErrorImportFailed', { message: err.params?.message })
            default:
                return err.code
        }
    }

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
                    <div className="hr-import-encoding-hint">
                        {t('hostResource.importEncodingHint')}
                    </div>
                    <div className="hr-import-sample-area">
                        <div className="hr-import-sample-header">
                            <span>{t('hostResource.importSampleTitle')}</span>
                            <button
                                type="button"
                                className="btn btn-secondary btn-xs"
                                onClick={downloadSample}
                                disabled={importing}
                            >
                                {t('hostResource.importDownloadSample')}
                            </button>
                        </div>
                        <pre className="hr-import-sample-content">{SAMPLE_CSVS[selectedType]}</pre>
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
                                        <span className="hr-import-error-row">Row {err.row}:</span> {translateError(err)}
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
