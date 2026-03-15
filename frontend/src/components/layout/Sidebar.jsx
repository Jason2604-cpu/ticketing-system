import { NavLink } from 'react-router-dom'
import { LayoutDashboard, Users, Building2, Settings, LogOut } from 'lucide-react'
import './Sidebar.css'

const navItems = [
  { to: '/',          icon: <LayoutDashboard size={16} />, label: 'Overview' },
  { to: '/engineers', icon: <Users size={16} />,           label: 'Engineers', badge: 5 },
  { to: '/companies', icon: <Building2 size={16} />,       label: 'Companies', badge: 5 },
  { to: '/settings',  icon: <Settings size={16} />,        label: 'Admin Settings' },
]

const Sidebar = () => {
  return (
    <aside className="sidebar">
      <div className="sidebar-logo">
        <div className="logo-icon">SD</div>
        <div>
          <p className="logo-title">SupportDesk</p>
          <p className="logo-sub">Admin Panel</p>
        </div>
      </div>

      <nav className="sidebar-nav">
        {navItems.map(({ to, icon, label, badge }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
          >
            {icon}
            <span>{label}</span>
            {badge && <span className="nav-badge">{badge}</span>}
          </NavLink>
        ))}
      </nav>

      <div className="sidebar-footer">
        <div className="user-avatar">SA</div>
        <div className="user-info">
          <p className="user-name">Super Admin</p>
          <p className="user-role">Service Provider</p>
        </div>
        <button className="logout-btn" onClick={() => {/* handle logout */}}>
          <LogOut size={15} />
        </button>
      </div>
    </aside>
  )
}

export default Sidebar