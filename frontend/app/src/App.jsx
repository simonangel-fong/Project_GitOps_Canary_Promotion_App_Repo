import { useState, useEffect } from 'react'
import './App.css'

function App() {
  const [version, setVersion] = useState('loading...')
  const [bgColor, setBgColor] = useState(null)

  useEffect(() => {
    fetch('/api')
      .then(res => {
        if (!res.ok) throw new Error(res.status)
        return res.json()
      })
      .then(data => {
        setVersion(data.version)
        if (data.bg_color) setBgColor(data.bg_color)
      })
      .catch(() => setVersion('unavailable'))
  }, [])

  return (
    <div className="page" style={bgColor ? { backgroundColor: bgColor } : undefined}>
      <h1>GitOps Demo App - {version}</h1>
    </div>
  )
}

export default App
