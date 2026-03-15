const API_URL = import.meta.env.VITE_API_BASE_URL

const authService = {

  login: async (email, password) => {
    const response = await fetch(`${API_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),

      // THIS IS THE KEY LINE
      // credentials: 'include' tells the browser:
      // "send and receive cookies with this request"
      // Without this, the browser ignores the cookie entirely
      credentials: 'include',
    })

    if (!response.ok) {
      throw new Error('Invalid email or password')
    }

    // We no longer get or store a token here
    // The browser handles the cookie automatically
    return true
  },

  logout: async () => {
    await fetch(`${API_URL}/auth/logout`, {
      method: 'POST',
      credentials: 'include',  // needed to send the cookie so backend can clear it
    })
  },

  // We can no longer check the token directly (it's HttpOnly)
  // Instead we ask the backend "am I still logged in?"
  checkAuth: async () => {
    const response = await fetch(`${API_URL}/auth/me`, {
      credentials: 'include',
    })
    return response.ok  // true = logged in, false = not
  }
}

export default authService