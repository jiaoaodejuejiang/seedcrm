import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearAuthSession, getAuthToken } from '../utils/auth'

const http = axios.create({
  baseURL: '/api',
  timeout: 20000
})

http.interceptors.request.use((config) => {
  const token = getAuthToken()
  config.headers = {
    ...(config.headers || {})
  }
  if (token) {
    config.headers['X-Auth-Token'] = token
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (payload && typeof payload.code !== 'undefined') {
      if (payload.code === 0) {
        return payload.data
      }

      const message = payload.message || '请求失败'
      if (!response.config?.silentError) {
        ElMessage.error(message)
      }
      return Promise.reject(new Error(message))
    }

    return payload
  },
  (error) => {
    if (error.response?.status === 401 || error.response?.data?.message?.includes('登录')) {
      clearAuthSession()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }

    const message = error.response?.data?.message || error.response?.data?.error || error.message || '网络请求失败'
    if (!error.config?.silentError) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default http
