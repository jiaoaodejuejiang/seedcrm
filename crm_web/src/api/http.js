import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '/api',
  timeout: 20000
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data

    if (payload && typeof payload.code !== 'undefined') {
      if (payload.code === 0) {
        return payload.data
      }

      const message = payload.message || '请求失败'
      ElMessage.error(message)
      return Promise.reject(new Error(message))
    }

    return payload
  },
  (error) => {
    const message =
      error.response?.data?.message ||
      error.response?.data?.error ||
      error.message ||
      '服务异常，请稍后重试'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default http
