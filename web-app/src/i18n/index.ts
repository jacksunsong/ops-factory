import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import en from './en.json'
import zh from './zh.json'

const getLanguageFromCookie=(name: string)=> {
    const cookies = document.cookie
    const cookieArray = cookies ? cookies.split('; ') : []

    for (const cookie of cookieArray) {
        const [cookieName,cookieValue] = cookie.split('=')
        if (cookieName === name && cookieValue) {
            return decodeURIComponent(cookieValue)
        }
    }
    return null
}

function resolveInitialLanguage(): 'zh' | 'en' {
    const locale = getLanguageFromCookie('locale')
    if (locale === 'en_US') {
        return 'en'
    }
    return 'zh'
}

i18n
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
        resources: {
            en: { translation: en },
            zh: { translation: zh },
        },
        lng: resolveInitialLanguage(),
        fallbackLng: 'zh',
        interpolation: {
            escapeValue: false,
        },
        detection: {
            order: ['cookie', 'navigator'],
            caches: ['cookie'],
        },
    })

export default i18n
