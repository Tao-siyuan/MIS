import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './styles/index.css'

const app = createApp(App)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.use(ElementPlus, { locale: zhCn })
app.use(router)

// 兜底错误处理：单个页面渲染异常不再导致整页白屏，同时定位到控制台
app.config.errorHandler = (err, instance, info) => {
  console.error('[MIS] 页面渲染异常', info, err)
  ElementPlus.ElMessage?.error?.('页面加载出错，已跳过该异常，请刷新或联系管理员')
}

// 路由懒加载失败（如发版后旧 chunk 失效）时回退到工作台，避免白屏
router.onError((err) => {
  console.error('[MIS] 路由加载失败', err)
  if (router.currentRoute.value.path !== '/dashboard') {
    router.replace('/dashboard')
  }
})

app.mount('#app')
