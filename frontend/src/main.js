import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import router from './router'
import './assets/styles/reset.css'
import { Capacitor } from '@capacitor/core'
import { StatusBar, Style } from '@capacitor/status-bar'
import { App as CapacitorApp } from '@capacitor/app'
import { useUserStore } from '@/stores/user'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

const userStore = useUserStore(pinia)
window.addEventListener('auth:expired', () => userStore.clearSession())

app.mount('#app')

if (Capacitor.isNativePlatform()) {
  document.documentElement.classList.add('native-app')
  StatusBar.setStyle({ style: Style.Light }).catch(() => {})
  StatusBar.setBackgroundColor({ color: '#10243F' }).catch(() => {})
  CapacitorApp.addListener('backButton', ({ canGoBack }) => {
    if (window.history.length > 1 && canGoBack) router.back()
    else CapacitorApp.minimizeApp()
  })
}
