import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './styles.css'
import App from './App.vue'
import router from './router'

createApp(App)
  .use(createPinia())
  .use(router)
  // 统一组件库分页、日期等内置文案为中文，避免工作台出现中英文混排。
  .use(ElementPlus, { locale: zhCn })
  .mount('#app')
