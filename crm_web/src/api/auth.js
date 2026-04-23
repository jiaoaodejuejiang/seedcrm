import axios from 'axios'

const authHttp = axios.create({
  baseURL: '/api',
  timeout: 20000
})

export function login(payload) {
  return authHttp.post('/auth/login', payload).then((response) => response.data?.data)
}

export function fetchCurrentUser(token) {
  return authHttp
    .get('/auth/me', {
      headers: {
        'X-Auth-Token': token
      }
    })
    .then((response) => response.data?.data)
}

export function logout(token) {
  return authHttp.post(
    '/auth/logout',
    {},
    {
      headers: {
        'X-Auth-Token': token
      }
    }
  )
}
