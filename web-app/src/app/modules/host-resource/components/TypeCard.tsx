import { useTranslation } from 'react-i18next'

function EditIcon() {
    return (
        <svg viewBox="0 0 20 20" fill="none" width="16" height="16" aria-hidden="true">
            <path
                d="M4.75 13.95 4 16l2.05-.75 8.5-8.5-1.3-1.3-8.5 8.5Z"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
            <path
                d="m11.95 6.05 1.3 1.3m.65-.65 1.05-1.05a1.15 1.15 0 0 0 0-1.6l-.5-.5a1.15 1.15 0 0 0-1.6 0L11.8 4.6"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
            <path
                d="M4 16h12"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinecap="round"
            />
        </svg>
    )
}

function TrashIcon() {
    return (
        <svg viewBox="0 0 20 20" fill="none" width="16" height="16" aria-hidden="true">
            <path
                d="M6.5 5.5h7m-6 0V4.75A1.75 1.75 0 0 1 9.25 3h1.5A1.75 1.75 0 0 1 12.5 4.75v.75m-8 0h11m-1 0-.6 8.39a1.75 1.75 0 0 1-1.75 1.61H7.85A1.75 1.75 0 0 1 6.1 13.89L5.5 5.5m2.75 2.5v4m4-4v4"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
        </svg>
    )
}

type TypeDef = {
    id: string
    name: string
    code: string
    description: string
    color: string
    knowledge: string
    mode?: 'peer' | 'primary-backup'
    createdAt: string
    updatedAt: string
}

type Props = {
    item: TypeDef
    onEdit: (item: TypeDef) => void
    onDelete: (item: TypeDef) => void
}

export default function TypeCard({ item, onEdit, onDelete }: Props) {
    const { t } = useTranslation()
    return (
        <div className="hr-type-def-card">
            <div className="hr-type-def-card-header">
                <span className="hr-type-def-card-color" style={{ background: item.color }} />
                <span className="hr-type-def-card-name">{item.name}</span>
                {item.mode === 'primary-backup' && (
                    <span className="hr-mode-badge">
                        {t('hostResource.clusterModePrimaryBackup')}
                    </span>
                )}
            </div>
            {item.description && (
                <div className="hr-type-def-card-desc">{item.description}</div>
            )}
            {item.knowledge && (
                <>
                    <div className="hr-type-def-card-knowledge-label">{t('hostResource.knowledge')}</div>
                    <div className="hr-type-def-card-knowledge">{item.knowledge}</div>
                </>
            )}
            <div className="hr-type-def-card-footer">
                <button
                    type="button"
                    className="hr-host-card-action"
                    onClick={() => onEdit(item)}
                    aria-label={t('common.edit')}
                    title={t('common.edit')}
                >
                    <EditIcon />
                </button>
                <button
                    type="button"
                    className="hr-host-card-action hr-host-card-action-danger"
                    onClick={() => onDelete(item)}
                    aria-label={t('common.delete')}
                    title={t('common.delete')}
                >
                    <TrashIcon />
                </button>
            </div>
        </div>
    )
}
