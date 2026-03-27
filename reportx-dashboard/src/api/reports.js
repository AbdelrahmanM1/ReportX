import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL + '/api',
  timeout: 10000,
})

api.interceptors.request.use(
  config => config,
  err => Promise.reject(err)
)

api.interceptors.response.use(
  res => res,
  err => {
    const status = err.response?.status

    if (status === 401) {
      localStorage.removeItem('reportx_staff_token')
      localStorage.removeItem('reportx_staff_token_expiry')
      localStorage.removeItem('reportx_staff_info')
      delete api.defaults.headers.common['Authorization']
      window.dispatchEvent(new CustomEvent('reportx-auth-expired'))
      return Promise.reject(err)
    }

    console.error('API error:', err.response?.data || err.message)
    return Promise.reject(err)
  }
)


export function setAuthToken(token) {
  if (token) {
    api.defaults.headers.common['Authorization'] = `Bearer ${token}`
  } else {
    delete api.defaults.headers.common['Authorization']
  }
}

export function clearAuthToken() {
  delete api.defaults.headers.common['Authorization']
}


export const exchangeToken = (pluginToken) =>
  api.post('/auth/exchange', { token: pluginToken })

export const authMe = () => api.get('/auth/me')


export const getReports   = (params)                  => api.get('/reports', { params })
export const getReport    = (id)                      => api.get(`/reports/${id}`)
export const updateStatus = (id, status, verdict)     => api.patch(`/reports/${id}/status`, { status, verdict })
export const addNote      = (id, note)                => api.post(`/reports/${id}/notes`, { note })


export const getStats    = ()       => api.get('/reports/stats')
export const getAuditLog = (params) => api.get('/audit-log', { params })


export const getPlayer         = (uuid) => api.get(`/players/${uuid}`)
export const searchPlayer      = (name) => api.get('/players/search', { params: { name } })
export const searchSuggestions = (name) => api.get('/players/search', { params: { name, suggestions: true } })