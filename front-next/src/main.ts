import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/tokens.css'
import './styles/app.css'
import { IS_TECHNICIAN_PORTAL } from './config/portal'

document.title = IS_TECHNICIAN_PORTAL ? 'FACE 技师工作台' : 'FACE 美容护理'

createApp(App).use(createPinia()).use(router).mount('#app')
