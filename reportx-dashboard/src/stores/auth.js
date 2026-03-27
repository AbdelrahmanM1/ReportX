import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as api from '../api/reports.js'

const TOKEN_KEY           = 'reportx_staff_token'
const TOKEN_EXPIRY_KEY    = 'reportx_staff_token_expiry'
const STAFF_DATA_KEY      = 'reportx_staff_info'
const SESSION_DURATION_MS = 1000 * 60 * 30

export const useAuthStore = defineStore('auth', () => {
  const token     = ref(localStorage.getItem(TOKEN_KEY) || '')
  const expiresAt = ref(Number(localStorage.getItem(TOKEN_EXPIRY_KEY) || '0'))
  const staffInfo = ref(JSON.parse(localStorage.getItem(STAFF_DATA_KEY) || 'null'))

  let logoutTimer = null

  const isAuthenticated = computed(() => {
    if (!token.value)     return false
    if (!expiresAt.value) return false
    return Date.now() < expiresAt.value
  })

  const role        = computed(() => staffInfo.value?.role || 'STAFF')
  const isModerator = computed(() => ['MODERATOR', 'SENIOR_STAFF', 'ADMIN', 'HEAD'].includes(role.value))
  const isElevated  = computed(() => ['ADMIN', 'HEAD'].includes(role.value))

  function applySession(jwt, info, expires) {
    token.value     = jwt
    staffInfo.value = info
    expiresAt.value = expires

    localStorage.setItem(TOKEN_KEY,        jwt)
    localStorage.setItem(TOKEN_EXPIRY_KEY, String(expires))
    localStorage.setItem(STAFF_DATA_KEY,   JSON.stringify(info))

    api.setAuthToken(jwt)

    if (logoutTimer) clearTimeout(logoutTimer)
    logoutTimer = setTimeout(() => logout(), Math.max(1000, expires - Date.now()))
  }

  async function login(pluginToken) {
    const { data } = await api.exchangeToken(pluginToken)
    const expires = Date.now() + SESSION_DURATION_MS
    applySession(data.accessToken, data.user, expires)
    return data.user
  }

  function logout() {
    token.value     = ''
    expiresAt.value = 0
    staffInfo.value = null

    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(TOKEN_EXPIRY_KEY)
    localStorage.removeItem(STAFF_DATA_KEY)

    api.clearAuthToken()

    if (logoutTimer) clearTimeout(logoutTimer)
    logoutTimer = null
  }


  function init() {
    if (!isAuthenticated.value) {
      return 
    }
    api.setAuthToken(token.value)
    if (!logoutTimer) {
      logoutTimer = setTimeout(
        () => logout(),
        Math.max(1000, expiresAt.value - Date.now())
      )
    }
  }

  function canViewSensitive() {
    return isElevated.value
  }

  return {
    token,
    expiresAt,
    staffInfo,
    isAuthenticated,
    role,
    isModerator,
    isElevated,
    canViewSensitive,
    login,
    logout,
    init,
  }
})