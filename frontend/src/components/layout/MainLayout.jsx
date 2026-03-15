import { Bell, Plus } from 'lucide-react'
import './Topbar.css'

const Topbar = ({ title, subtitle, onAdd, addLabel }) => {
  return (
    <header className="topbar">
      <div className="topbar-left">
        <h1 className="topbar-title">{title}</h1>
        {subtitle && <p className="topbar-sub">{subtitle}</p>}
      </div>
      <div className="topbar-right">
        <div className="search-box">
          <input type="text" placeholder="Search..." />
        </div>
        <button className="notif-btn"><Bell size={15} /></button>
        {onAdd && (
          <button className="add-btn" onClick={onAdd}>
            <Plus size={13} />
            {addLabel || 'Add'}
          </button>
        )}
      </div>
    </header>
  )
}

export default Topbar