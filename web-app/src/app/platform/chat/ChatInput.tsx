import { useState, useRef, useEffect, useCallback, useMemo, KeyboardEvent, ChangeEvent, DragEvent, ClipboardEvent, SyntheticEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { TokenState, ImageData } from '@goosed/sdk'
import AgentSelector from './AgentSelector'
import { compressImageDataUrl, isImageFile, parseDataUrl, readFileAsDataUrl } from '../../../utils/imageUtils'
import { useVoiceInput } from './useVoiceInput'
import { useToast } from '../providers/ToastContext'
import type { AttachedFile, SelectedSkill } from '../../../types/message'
import type { SkillEntry } from '../../../types/skill'
import './ChatInput.css'

// File handling constants
const MAX_IMAGES_PER_MESSAGE = 3
const MAX_FILES_PER_MESSAGE = 5
const MAX_FILE_SIZE_MB = 10
const MAX_IMAGE_SIZE_MB = 5
const MAX_VISIBLE_SKILL_OPTIONS = 25
const SUPPORTED_FILE_TYPES = [
    // Text files
    '.txt', '.md', '.json', '.yaml', '.yml', '.xml', '.csv', '.tsv',
    // Code files
    '.js', '.ts', '.jsx', '.tsx', '.py', '.java', '.c', '.cpp', '.h', '.hpp',
    '.go', '.rs', '.rb', '.php', '.swift', '.kt', '.scala', '.sh', '.bash',
    '.css', '.scss', '.less', '.html', '.vue', '.svelte',
    // Config files
    '.env', '.gitignore', '.dockerignore', '.editorconfig', '.prettierrc',
    '.eslintrc', '.babelrc', 'Dockerfile', 'Makefile',
    // Document files
    '.pdf', '.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx',
    // Images
    '.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg',
    // Archives
    '.zip', '.tar', '.gz',
]

interface UploadedFile {
    id: string
    file: File
    name: string
    type: string
    size: number
    isImage: boolean
    preview?: string       // Data URL for image preview (compressed)
    uploadedPath?: string  // Server path for non-image files after upload
    isLoading: boolean
    error?: string
}

function getSkillId(skill: SkillEntry): string {
    if (skill.id) return skill.id
    const segments = skill.path.split('/').filter(Boolean)
    return segments[segments.length - 1] || skill.name
}

function toSelectedSkill(skill: SkillEntry): SelectedSkill {
    return {
        id: getSkillId(skill),
        name: skill.name,
        description: skill.description,
        path: skill.path || `skills/${getSkillId(skill)}`,
    }
}

function getSkillSearchText(skill: SkillEntry): string {
    return [skill.id, skill.name, skill.description, skill.path]
        .filter((value): value is string => typeof value === 'string' && value.length > 0)
        .join(' ')
        .toLowerCase()
}

function getSkillTitle(skill: SkillEntry): string {
    return [skill.name, skill.path, skill.description?.trim()]
        .filter((value): value is string => typeof value === 'string' && value.length > 0)
        .join('\n')
}

function isTruthyMetadata(value: unknown): boolean {
    if (typeof value === 'boolean') return value
    if (typeof value === 'string') {
        return ['true', '1', 'yes', 'y'].includes(value.trim().toLowerCase())
    }
    return false
}

function getDisplayOrder(skill: SkillEntry): number {
    if (typeof skill.displayOrder === 'number' && Number.isFinite(skill.displayOrder)) {
        return skill.displayOrder
    }
    if (typeof skill.displayOrder === 'string') {
        const parsed = Number(skill.displayOrder)
        if (Number.isFinite(parsed)) return parsed
    }
    return 0
}

function compareSkills(a: SkillEntry, b: SkillEntry): number {
    const pinnedDelta = Number(isTruthyMetadata(b.pinned)) - Number(isTruthyMetadata(a.pinned))
    if (pinnedDelta !== 0) return pinnedDelta

    const orderDelta = getDisplayOrder(a) - getDisplayOrder(b)
    if (orderDelta !== 0) return orderDelta

    return a.name.localeCompare(b.name)
}

function findSlashSkillToken(text: string, cursor: number): { start: number; end: number; query: string } | null {
    const beforeCursor = text.slice(0, cursor)
    const tokenStart = Math.max(
        beforeCursor.lastIndexOf(' '),
        beforeCursor.lastIndexOf('\n'),
        beforeCursor.lastIndexOf('\t'),
    ) + 1
    const token = beforeCursor.slice(tokenStart)
    if (!token.startsWith('/')) return null
    if (token.slice(1).includes('/')) return null
    return { start: tokenStart, end: cursor, query: token.slice(1) }
}

interface ChatInputProps {
    onSubmit: (message: string, images?: ImageData[], attachedFiles?: AttachedFile[], selectedSkill?: SelectedSkill) => void
    onStopGeneration?: () => void | Promise<void>
    onUploadFile?: (file: File) => Promise<{ path: string }>
    disabled?: boolean
    isGenerating?: boolean
    placeholder?: string
    autoFocus?: boolean
    selectedAgent?: string
    onAgentChange?: (agentId: string) => void
    showAgentSelector?: boolean
    modelInfo?: { provider: string; model: string } | null
    tokenState?: TokenState | null
    presetMessage?: string
    presetToken?: number
    skills?: SkillEntry[]
    onBrowseSkillMarket?: () => void
}

export default function ChatInput({
    onSubmit,
    onStopGeneration,
    onUploadFile,
    disabled = false,
    isGenerating = false,
    placeholder = "Type a message...",
    autoFocus = false,
    selectedAgent = '',
    onAgentChange,
    showAgentSelector = true,
    modelInfo,
    tokenState,
    presetMessage,
    presetToken,
    skills = [],
    onBrowseSkillMarket,
}: ChatInputProps) {
    const { t, i18n } = useTranslation()
    const { showToast } = useToast()
    const [value, setValue] = useState('')
    const [uploadedFiles, setUploadedFiles] = useState<UploadedFile[]>([])
    const [isDragging, setIsDragging] = useState(false)
    const [isTemplateAppliedFlash, setIsTemplateAppliedFlash] = useState(false)
    const [selectedSkill, setSelectedSkill] = useState<SelectedSkill | null>(null)
    const [isSkillPickerOpen, setIsSkillPickerOpen] = useState(false)
    const [skillQuery, setSkillQuery] = useState('')
    const [skillSlashRange, setSkillSlashRange] = useState<{ start: number; end: number } | null>(null)
    const [activeSkillIndex, setActiveSkillIndex] = useState(0)
    const textareaRef = useRef<HTMLTextAreaElement>(null)
    const fileInputRef = useRef<HTMLInputElement>(null)

    const sortedSkills = useMemo(() => [...skills].sort(compareSkills), [skills])
    const filteredSkills = useMemo(() => sortedSkills.filter(skill => {
        const query = skillQuery.trim().toLowerCase()
        if (!query) return true
        return getSkillSearchText(skill).includes(query)
    }), [skillQuery, sortedSkills])
    const visibleSkills = useMemo(
        () => filteredSkills.slice(0, MAX_VISIBLE_SKILL_OPTIONS),
        [filteredSkills],
    )
    const hiddenSkillCount = Math.max(0, filteredSkills.length - visibleSkills.length)

    const activeSkill = visibleSkills[activeSkillIndex]

    const closeSkillPicker = useCallback(() => {
        setIsSkillPickerOpen(false)
        setSkillQuery('')
        setSkillSlashRange(null)
        setActiveSkillIndex(0)
    }, [])

    const updateSkillPickerState = useCallback((nextValue: string, cursor: number | null) => {
        if (disabled || cursor == null) {
            closeSkillPicker()
            return
        }
        const slashToken = findSlashSkillToken(nextValue, cursor)
        if (!slashToken) {
            closeSkillPicker()
            return
        }
        setSkillQuery(slashToken.query)
        setSkillSlashRange({ start: slashToken.start, end: slashToken.end })
        setIsSkillPickerOpen(true)
        setActiveSkillIndex(0)
    }, [closeSkillPicker, disabled])

    const { state: voiceState, isSupported: voiceSupported, startListening, stopListening, error: voiceError } = useVoiceInput({
        onTranscript: (text) => setValue(text),
        lang: i18n.language.startsWith('zh') ? 'zh-CN' : 'en-US',
    })
    const isListening = voiceState === 'listening'

    // Show voice input errors as toast
    useEffect(() => {
        if (!voiceError) return
        if (voiceError === 'mic-permission-denied') {
            showToast('error', t('errors.micPermissionDenied'))
        } else {
            showToast('error', t('errors.voiceError', { error: voiceError }))
        }
    }, [voiceError, showToast, t])

    // Auto-resize textarea
    useEffect(() => {
        const textarea = textareaRef.current
        if (textarea) {
            textarea.style.height = 'auto'
            textarea.style.height = `${Math.min(textarea.scrollHeight, 200)}px`
        }
    }, [value])

    // Auto focus
    useEffect(() => {
        if (autoFocus && textareaRef.current) {
            textareaRef.current.focus()
        }
    }, [autoFocus])

    useEffect(() => {
        setSelectedSkill(null)
        closeSkillPicker()
    }, [selectedAgent, closeSkillPicker])

    useEffect(() => {
        if (activeSkillIndex < visibleSkills.length) return
        setActiveSkillIndex(Math.max(0, visibleSkills.length - 1))
    }, [activeSkillIndex, visibleSkills.length])

    useEffect(() => {
        if (!selectedSkill) return
        const stillInstalled = skills.some(skill => getSkillId(skill) === selectedSkill.id)
        if (!stillInstalled) {
            setSelectedSkill(null)
        }
    }, [selectedSkill, skills])

    // Fill input from external template selection without submitting.
    useEffect(() => {
        if (typeof presetToken !== 'number') return
        setValue(presetMessage || '')
        closeSkillPicker()
        setIsTemplateAppliedFlash(true)
        if (textareaRef.current) {
            textareaRef.current.focus()
        }

        const timer = window.setTimeout(() => {
            setIsTemplateAppliedFlash(false)
        }, 1200)

        return () => window.clearTimeout(timer)
    }, [presetToken, presetMessage, closeSkillPicker])

    const handleSelectSkill = (skill: SkillEntry) => {
        const nextSkill = toSelectedSkill(skill)
        setSelectedSkill(nextSkill)
        if (skillSlashRange) {
            const nextValue = `${value.slice(0, skillSlashRange.start)}${value.slice(skillSlashRange.end)}`
            setValue(nextValue)
            window.requestAnimationFrame(() => {
                const textarea = textareaRef.current
                if (!textarea) return
                textarea.focus()
                textarea.setSelectionRange(skillSlashRange.start, skillSlashRange.start)
            })
        } else {
            textareaRef.current?.focus()
        }
        closeSkillPicker()
    }

    const handleRemoveSelectedSkill = () => {
        setSelectedSkill(null)
        textareaRef.current?.focus()
    }

    const handleBrowseSkillMarket = () => {
        closeSkillPicker()
        onBrowseSkillMarket?.()
    }

    const isFileTypeSupported = (file: File): boolean => {
        const extension = '.' + file.name.split('.').pop()?.toLowerCase()
        return SUPPORTED_FILE_TYPES.includes(extension) || file.name === 'Dockerfile' || file.name === 'Makefile'
    }

    const currentImageCount = uploadedFiles.filter(f => f.isImage && !f.error).length
    const currentFileCount = uploadedFiles.filter(f => !f.isImage && !f.error).length

    const processImageFile = useCallback(async (file: File, fileId: string) => {
        try {
            const dataUrl = await readFileAsDataUrl(file)
            const compressed = await compressImageDataUrl(dataUrl)
            setUploadedFiles(prev =>
                prev.map(f => f.id === fileId ? { ...f, preview: compressed, isLoading: false } : f)
            )
        } catch (err) {
            setUploadedFiles(prev =>
                prev.map(f => f.id === fileId ? {
                    ...f, isLoading: false,
                    error: `Failed to process image: ${err instanceof Error ? err.message : 'Unknown error'}`
                } : f)
            )
        }
    }, [])

    const processNonImageFile = useCallback(async (file: File, fileId: string) => {
        if (!onUploadFile) {
            setUploadedFiles(prev =>
                prev.map(f => f.id === fileId ? { ...f, isLoading: false, error: 'File upload not available' } : f)
            )
            return
        }
        try {
            const result = await onUploadFile(file)
            setUploadedFiles(prev =>
                prev.map(f => f.id === fileId ? { ...f, uploadedPath: result.path, isLoading: false } : f)
            )
        } catch (err) {
            setUploadedFiles(prev =>
                prev.map(f => f.id === fileId ? {
                    ...f, isLoading: false,
                    error: `Upload failed: ${err instanceof Error ? err.message : 'Unknown error'}`
                } : f)
            )
        }
    }, [onUploadFile])

    const handleFileSelect = async (files: FileList | null) => {
        if (!files || files.length === 0) return

        const incoming = Array.from(files)

        // Separate images and non-images
        const imageFiles = incoming.filter(f => isImageFile(f))
        const nonImageFiles = incoming.filter(f => !isImageFile(f))

        const allowedImages = imageFiles.slice(0, MAX_IMAGES_PER_MESSAGE - currentImageCount)
        const allowedFiles = nonImageFiles.slice(0, MAX_FILES_PER_MESSAGE - currentFileCount)

        if (allowedImages.length < imageFiles.length) {
            showToast('warning', t('chat.maxImagesAllowed', { max: MAX_IMAGES_PER_MESSAGE }))
        }
        if (allowedFiles.length < nonImageFiles.length) {
            showToast('warning', t('chat.maxFilesAllowed', { max: MAX_FILES_PER_MESSAGE }))
        }

        const allFiles = [...allowedImages, ...allowedFiles]
        if (allFiles.length === 0) return

        // Add files with loading state
        const newEntries: UploadedFile[] = allFiles.map(file => {
            const isImage = isImageFile(file)
            const maxSize = isImage ? MAX_IMAGE_SIZE_MB : MAX_FILE_SIZE_MB

            const entry: UploadedFile = {
                id: `file-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`,
                file,
                name: file.name,
                type: file.type,
                size: file.size,
                isImage,
                isLoading: true,
            }

            // Check file type
            if (!isImage && !isFileTypeSupported(file)) {
                return { ...entry, isLoading: false, error: `Unsupported file type.` }
            }

            // Check file size
            if (file.size > maxSize * 1024 * 1024) {
                return {
                    ...entry, isLoading: false,
                    error: `File too large (${(file.size / (1024 * 1024)).toFixed(1)}MB). Max: ${maxSize}MB`
                }
            }

            return entry
        })

        setUploadedFiles(prev => [...prev, ...newEntries])

        // Process files in parallel
        for (const entry of newEntries) {
            if (entry.error) continue
            if (entry.isImage) {
                processImageFile(entry.file, entry.id)
            } else {
                processNonImageFile(entry.file, entry.id)
            }
        }

        // Reset file input
        if (fileInputRef.current) {
            fileInputRef.current.value = ''
        }
    }

    const handleDragOver = (e: DragEvent<HTMLDivElement>) => {
        e.preventDefault()
        e.stopPropagation()
        setIsDragging(true)
    }

    const handleDragLeave = (e: DragEvent<HTMLDivElement>) => {
        e.preventDefault()
        e.stopPropagation()
        setIsDragging(false)
    }

    const handleDrop = (e: DragEvent<HTMLDivElement>) => {
        e.preventDefault()
        e.stopPropagation()
        setIsDragging(false)

        const files = e.dataTransfer.files
        handleFileSelect(files)
    }

    const handlePaste = async (e: ClipboardEvent<HTMLTextAreaElement>) => {
        const items = e.clipboardData?.files
        if (!items || items.length === 0) return

        const imageFiles = Array.from(items).filter(f => isImageFile(f))
        if (imageFiles.length === 0) return

        e.preventDefault()

        const remaining = MAX_IMAGES_PER_MESSAGE - currentImageCount
        if (remaining <= 0) {
            showToast('warning', t('chat.maxImagesAllowed', { max: MAX_IMAGES_PER_MESSAGE }))
            return
        }

        const toProcess = imageFiles.slice(0, remaining)
        const newEntries: UploadedFile[] = toProcess.map((file, i) => ({
            id: `paste-${Date.now()}-${i}`,
            file,
            name: file.name || `pasted-image-${Date.now()}.png`,
            type: file.type,
            size: file.size,
            isImage: true,
            isLoading: true,
        }))

        setUploadedFiles(prev => [...prev, ...newEntries])

        for (const entry of newEntries) {
            processImageFile(entry.file, entry.id)
        }
    }

    const handleRemoveFile = (id: string) => {
        setUploadedFiles(prev => prev.filter(f => f.id !== id))
    }

    const handleSubmit = () => {
        if (disabled) return
        if (isSkillPickerOpen) return

        // Extract images as ImageData[]
        const images: ImageData[] = uploadedFiles
            .filter(f => f.isImage && f.preview && !f.error && !f.isLoading)
            .map(f => parseDataUrl(f.preview!))
            .filter((parsed): parsed is { data: string; mimeType: string } => parsed !== null)

        // Collect non-image files as AttachedFile metadata (not appended to text)
        const attachedFiles: AttachedFile[] = uploadedFiles
            .filter(f => !f.isImage && f.uploadedPath && !f.error && !f.isLoading)
            .map(f => {
                const fileName = f.name
                const ext = fileName.includes('.') ? fileName.split('.').pop()?.toLowerCase() || '' : ''
                const pathBasename = f.uploadedPath!.split('/').pop() || f.uploadedPath!
                return { name: fileName, path: pathBasename, ext, serverPath: f.uploadedPath! }
            })

        const text = value.trim()

        if (!text && images.length === 0 && attachedFiles.length === 0 && !selectedSkill) return

        onSubmit(
            text,
            images.length > 0 ? images : undefined,
            attachedFiles.length > 0 ? attachedFiles : undefined,
            selectedSkill || undefined
        )
        setValue('')
        setUploadedFiles([])
        setSelectedSkill(null)
        closeSkillPicker()
        // Reset textarea height
        if (textareaRef.current) {
            textareaRef.current.style.height = 'auto'
        }
    }

    const handleStopGeneration = () => {
        if (!isGenerating || !onStopGeneration) return
        onStopGeneration()
    }

    const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
        if (isSkillPickerOpen) {
            if (e.key === 'ArrowDown') {
                e.preventDefault()
                setActiveSkillIndex(index => visibleSkills.length === 0 ? 0 : (index + 1) % visibleSkills.length)
                return
            }
            if (e.key === 'ArrowUp') {
                e.preventDefault()
                setActiveSkillIndex(index => visibleSkills.length === 0 ? 0 : (index - 1 + visibleSkills.length) % visibleSkills.length)
                return
            }
            if (e.key === 'Enter' || e.key === 'Tab') {
                e.preventDefault()
                if (activeSkill) {
                    handleSelectSkill(activeSkill)
                    return
                }
                return
            }
            if (e.key === 'Escape') {
                e.preventDefault()
                closeSkillPicker()
                return
            }
        }
        if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
            e.preventDefault()
            handleSubmit()
        }
    }

    const handleChange = (e: ChangeEvent<HTMLTextAreaElement>) => {
        const nextValue = e.target.value
        setValue(nextValue)
        updateSkillPickerState(nextValue, e.target.selectionStart)
    }

    const handleSelectionChange = (e: SyntheticEvent<HTMLTextAreaElement>) => {
        const textarea = e.currentTarget
        updateSkillPickerState(textarea.value, textarea.selectionStart)
    }

    const handleFileInputClick = () => {
        fileInputRef.current?.click()
    }

    const hasContent = value.trim() || selectedSkill || uploadedFiles.some(f => !f.error && !f.isLoading)
    const totalFileCount = uploadedFiles.length
    const maxTotalFiles = MAX_IMAGES_PER_MESSAGE + MAX_FILES_PER_MESSAGE
    const isAnyFileLoading = uploadedFiles.some(f => f.isLoading)

    const handleQuickContinue = useCallback(() => {
        if (disabled || isGenerating || isListening || isAnyFileLoading) return
        onSubmit(t('chat.quickContinue'))
        textareaRef.current?.focus()
    }, [disabled, isGenerating, isListening, isAnyFileLoading, onSubmit, t])

    return (
        <div
            className={`chat-input-container ${isDragging ? 'dragging' : ''} ${isTemplateAppliedFlash ? 'template-applied' : ''}`}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
        >
            {/* Hidden file input */}
            <input
                ref={fileInputRef}
                type="file"
                multiple
                accept={SUPPORTED_FILE_TYPES.join(',')}
                onChange={(e) => handleFileSelect(e.target.files)}
                style={{ display: 'none' }}
            />

            {/* Drag overlay */}
            {isDragging && (
                <div className="drag-overlay">
                    <div className="drag-overlay-content">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="48" height="48">
                            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                            <polyline points="17 8 12 3 7 8" />
                            <line x1="12" y1="3" x2="12" y2="15" />
                        </svg>
                        <p>{t('chat.dropFilesHere')}</p>
                    </div>
                </div>
            )}

            {isSkillPickerOpen && (
                <div className="skill-picker" role="listbox" aria-label={t('chat.skillPickerLabel')}>
                    <div className="skill-picker-header">
                        <span>{t('chat.skillPickerTitle')}</span>
                        <span className="skill-picker-header-meta">
                            {t('chat.skillPickerResultCount', { shown: visibleSkills.length, total: filteredSkills.length })}
                            {skillQuery && <span className="skill-picker-query">/{skillQuery}</span>}
                        </span>
                    </div>
                    {skills.length === 0 ? (
                        <div className="skill-picker-empty">
                            <p>{t('chat.skillPickerNoSkills')}</p>
                            {onBrowseSkillMarket && (
                                <button type="button" className="skill-picker-market-btn" onClick={handleBrowseSkillMarket}>
                                    {t('chat.skillPickerBrowseMarket')}
                                </button>
                            )}
                        </div>
                    ) : filteredSkills.length === 0 ? (
                        <div className="skill-picker-empty">
                            <p>{t('chat.skillPickerNoMatches')}</p>
                        </div>
                    ) : (
                        <>
                        <div className="skill-picker-list">
                            {visibleSkills.map((skill, index) => {
                                const skillId = getSkillId(skill)
                                const description = skill.description?.trim()
                                const skillTitle = getSkillTitle(skill)
                                return (
                                    <button
                                        key={skillId}
                                        type="button"
                                        className={`skill-picker-option ${index === activeSkillIndex ? 'active' : ''}`}
                                        onMouseDown={event => event.preventDefault()}
                                        onClick={() => handleSelectSkill(skill)}
                                        title={skillTitle}
                                        role="option"
                                        aria-selected={index === activeSkillIndex}
                                    >
                                        <span className="skill-picker-option-icon" aria-hidden="true">
                                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="16" height="16">
                                                <path d="M12 3 4.5 7.25 12 11.5l7.5-4.25L12 3Z" />
                                                <path d="m4.5 12.25 7.5 4.25 7.5-4.25" />
                                                <path d="m4.5 17.25 7.5 4.25 7.5-4.25" />
                                            </svg>
                                        </span>
                                        <span className="skill-picker-option-body">
                                            <span className="skill-picker-option-name">{skill.name}</span>
                                            <span className="skill-picker-option-desc">
                                                {description || t('skill.noDescription')}
                                            </span>
                                        </span>
                                    </button>
                                )
                            })}
                        </div>
                        {hiddenSkillCount > 0 && (
                            <div className="skill-picker-footer">
                                {t('chat.skillPickerMoreResults', { count: hiddenSkillCount })}
                            </div>
                        )}
                        </>
                    )}
                </div>
            )}

            {selectedSkill && (
                <div className="selected-skill-row">
                    <span className="selected-skill-chip" title={[selectedSkill.name, selectedSkill.path, selectedSkill.description].filter(Boolean).join('\n')}>
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="15" height="15" aria-hidden="true">
                            <path d="M12 3 4.5 7.25 12 11.5l7.5-4.25L12 3Z" />
                            <path d="m4.5 12.25 7.5 4.25 7.5-4.25" />
                        </svg>
                        <span className="selected-skill-name">{selectedSkill.name}</span>
                        <button
                            type="button"
                            className="selected-skill-remove"
                            onClick={handleRemoveSelectedSkill}
                            aria-label={t('chat.removeSelectedSkill', { name: selectedSkill.name })}
                            title={t('chat.removeSelectedSkill', { name: selectedSkill.name })}
                        >
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="13" height="13" aria-hidden="true">
                                <line x1="18" y1="6" x2="6" y2="18" />
                                <line x1="6" y1="6" x2="18" y2="18" />
                            </svg>
                        </button>
                    </span>
                </div>
            )}

            {/* Uploaded files preview */}
            {uploadedFiles.length > 0 && (
                <div className="uploaded-files">
                    {uploadedFiles.map(file => (
                        <div key={file.id} className={`uploaded-file ${file.error ? 'error' : ''}`}>
                            {file.isImage && file.preview ? (
                                <img src={file.preview} alt={file.name} className="uploaded-file-preview" />
                            ) : (
                                <div className="uploaded-file-icon">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="20" height="20">
                                        <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" />
                                        <polyline points="13 2 13 9 20 9" />
                                    </svg>
                                </div>
                            )}
                            <div className="uploaded-file-info">
                                <span className="uploaded-file-name">{file.name}</span>
                                {file.error ? (
                                    <span className="uploaded-file-error">{file.error}</span>
                                ) : file.isLoading ? (
                                    <span className="uploaded-file-loading">{t('common.loading')}</span>
                                ) : (
                                    <span className="uploaded-file-size">
                                        {(file.size / 1024).toFixed(1)} KB
                                    </span>
                                )}
                            </div>
                            <button
                                className="uploaded-file-remove"
                                onClick={() => handleRemoveFile(file.id)}
                                aria-label={t('chat.removeFile')}
                            >
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16">
                                    <line x1="18" y1="6" x2="6" y2="18" />
                                    <line x1="6" y1="6" x2="18" y2="18" />
                                </svg>
                            </button>
                        </div>
                    ))}
                </div>
            )}

            <div className="chat-input-wrapper">
                <textarea
                    ref={textareaRef}
                    className="chat-input"
                    value={value}
                    onChange={handleChange}
                    onKeyDown={handleKeyDown}
                    onSelect={handleSelectionChange}
                    onPaste={handlePaste}
                    placeholder={placeholder}
                    disabled={disabled}
                    rows={1}
                />
            </div>

            {/* Toolbar */}
            <div className="chat-input-toolbar">
                {/* Attach button */}
                <button
                    className="toolbar-btn"
                    onClick={handleFileInputClick}
                    disabled={disabled || totalFileCount >= maxTotalFiles}
                    title={t('chat.attachFiles', { current: totalFileCount, max: maxTotalFiles })}
                >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.3" width="17" height="17">
                        <path d="M12 5v14M5 12h14" />
                    </svg>
                    {totalFileCount > 0 && (
                        <span className="toolbar-badge">{totalFileCount}</span>
                    )}
                </button>

                <div className="toolbar-divider" />

                {/* Model info */}
                {modelInfo && (
                    <>
                        <div className="toolbar-item toolbar-model-info">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14">
                                <circle cx="12" cy="12" r="3" />
                                <path d="M12 1v4M12 19v4M4.22 4.22l2.83 2.83M16.95 16.95l2.83 2.83M1 12h4M19 12h4M4.22 19.78l2.83-2.83M16.95 7.05l2.83-2.83" />
                            </svg>
                            <span className="toolbar-text">{modelInfo.model}</span>
                        </div>
                        <div className="toolbar-divider" />
                    </>
                )}

                {/* Token usage */}
                {tokenState && tokenState.accumulatedTotalTokens > 0 && (
                    <>
                        <div className="toolbar-item toolbar-token-info" title={`Input: ${tokenState.accumulatedInputTokens.toLocaleString()} / Output: ${tokenState.accumulatedOutputTokens.toLocaleString()}`}>
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14">
                                <path d="M20.24 12.24a6 6 0 0 0-8.49-8.49L5 10.5V19h8.5z" />
                                <line x1="16" y1="8" x2="2" y2="22" />
                                <line x1="17.5" y1="15" x2="9" y2="15" />
                            </svg>
                            <span className="toolbar-text">{tokenState.accumulatedTotalTokens.toLocaleString()}</span>
                        </div>
                        <div className="toolbar-divider" />
                    </>
                )}

                {/* Agent selector */}
                {showAgentSelector && onAgentChange && (
                    <AgentSelector
                        selectedAgent={selectedAgent}
                        onAgentChange={onAgentChange}
                        disabled={disabled}
                    />
                )}

                {(() => {
                    const showMic = !hasContent && !isGenerating && !isListening && voiceSupported
                    const canQuickContinue = !(disabled || isGenerating || isListening || isAnyFileLoading)
                    return (
                        <div className="chat-send-actions">
                            <button
                                type="button"
                                className="chat-quick-continue-btn"
                                onClick={handleQuickContinue}
                                disabled={!canQuickContinue}
                                aria-label={t('chat.quickContinue')}
                                title={t('chat.quickContinue')}
                            >
                                {t('chat.quickContinue')}
                            </button>

                            <button
                                className={`chat-send-btn-new ${isGenerating ? 'is-stop' : ''} ${isListening ? 'is-recording' : ''}`}
                                onClick={
                                    isGenerating ? handleStopGeneration :
                                    isListening ? stopListening :
                                    showMic ? startListening :
                                    handleSubmit
                                }
                                disabled={
                                    isGenerating ? !onStopGeneration :
                                    isListening ? false :
                                    showMic ? disabled :
                                    (disabled || !hasContent || isAnyFileLoading)
                                }
                                aria-label={
                                    isListening ? t('chat.stopRecording') :
                                    showMic ? t('chat.voiceInput') :
                                    isGenerating ? t('chat.stopGeneration') :
                                    t('chat.sendMessage')
                                }
                                title={
                                    isListening ? t('chat.stopRecording') :
                                    showMic ? t('chat.voiceInput') :
                                    isGenerating ? t('chat.stopGeneration') :
                                    t('chat.sendMessage')
                                }
                            >
                                {isGenerating ? (
                                    <svg className="chat-send-btn-icon is-stop" viewBox="0 0 24 24" fill="currentColor">
                                        <rect x="5.5" y="5.5" width="13" height="13" rx="2.1" />
                                    </svg>
                                ) : isListening ? (
                                    <svg className="chat-send-btn-icon is-stop" viewBox="0 0 24 24" fill="currentColor">
                                        <rect x="5.5" y="5.5" width="13" height="13" rx="2.1" />
                                    </svg>
                                ) : showMic ? (
                                    <svg className="chat-send-btn-icon is-mic" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                        <rect x="9" y="1" width="6" height="12" rx="3" />
                                        <path d="M19 10v1a7 7 0 0 1-14 0v-1" />
                                        <line x1="12" y1="18" x2="12" y2="23" />
                                        <line x1="8" y1="23" x2="16" y2="23" />
                                    </svg>
                                ) : (
                                    <svg className="chat-send-btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4">
                                        <path d="M12 19V5" />
                                        <path d="M6.5 10.5L12 5l5.5 5.5" />
                                    </svg>
                                )}
                            </button>
                        </div>
                    )
                })()}
            </div>
        </div>
    )
}
