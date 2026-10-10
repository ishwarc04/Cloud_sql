import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiRequest } from '../app/api'
import { formatBytes } from '../features/databases/format'
import { PaymentHistory, type PaymentReport } from '../features/billing/PaymentHistory'
interface Billing { plan: { name: string; maximumDatabases: number; storagePerDatabaseBytes: number } }
interface Checkout { orderId: string; keyId: string; amount: number; currency: string }
interface Confirmation { razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string }
interface RazorpayOptions { key: string; amount: number; currency: string; order_id: string; name: string; description: string; handler: (result: Confirmation) => void; modal: { ondismiss: () => void }; theme: { color: string } }
declare global { interface Window { Razorpay?: new (options: RazorpayOptions) => { open(): void; on(event: string, callback: () => void): void } } }
let checkoutScript: Promise<void> | undefined
function loadCheckout() {
  if (window.Razorpay) return Promise.resolve()
  if (!checkoutScript) checkoutScript = new Promise<void>((resolve, reject) => {
    const script = document.createElement('script'); script.src = 'https://checkout.razorpay.com/v1/checkout.js'
    script.onload = () => resolve(); script.onerror = () => { script.remove(); checkoutScript = undefined; reject(new Error('Unable to load Razorpay checkout. Please retry.')) }; document.head.append(script)
  })
  return checkoutScript
}
export function BillingPage() {
  const [data, setData] = useState<Billing | null>(null)
  const [history, setHistory] = useState<PaymentReport | null>(null)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  async function reload() {
    const [plan, payments] = await Promise.all([apiRequest<Billing>('/api/billing'), apiRequest<PaymentReport>('/api/billing/payments')]); setData(plan); setHistory(payments)
  }
  useEffect(() => { let active = true; Promise.all([apiRequest<Billing>('/api/billing'), apiRequest<PaymentReport>('/api/billing/payments')]).then(([plan, payments]) => { if (active) { setData(plan); setHistory(payments) } }).catch((e: unknown) => { if (active) setError(e instanceof Error ? e.message : 'Unable to load plan.') }); return () => { active = false } }, [])
  async function refreshOrder(id: string) {
    setBusy(true); setError('')
    try { await apiRequest<PaymentReport>(`/api/billing/orders/${encodeURIComponent(id)}/refresh`, { method: 'POST' }); await reload(); setMessage('Payment status checked. Only captured payments activate Pro.') }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to check payment.') }
    finally { setBusy(false) }
  }
  async function pay() {
    setBusy(true); setError(''); setMessage('')
    try {
      await loadCheckout()
      const order = await apiRequest<Checkout>('/api/billing/orders', { method: 'POST' })
      if (!window.Razorpay) throw new Error('Checkout is unavailable.')
      const checkout = new window.Razorpay({ key: order.keyId, amount: order.amount, currency: order.currency, order_id: order.orderId, name: 'CloudSQL Lab', description: 'Lab Pro — test payment', theme: { color: '#AA6108' },
        handler: result => { void (async () => {
          try { await apiRequest('/api/billing/verify', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ orderId: result.razorpay_order_id, paymentId: result.razorpay_payment_id, signature: result.razorpay_signature }) }); await reload(); setMessage('Test payment verified. Pro is now active.') }
          catch (e) { setError(e instanceof Error ? e.message : 'Verification failed. Use Check status in payment history to retry.') }
          finally { setBusy(false) }
        })() }, modal: { ondismiss: () => { setBusy(false); setMessage('Checkout closed. You can resume checkout or check payment status below.'); void reload().catch(() => {}) } } })
      checkout.on('payment.failed', () => { setError('Payment attempt failed. Retry checkout; Pro is granted only after verification.'); setBusy(false); void reload().catch(() => {}) })
      await reload(); checkout.open()
    } catch (e) { setError(e instanceof Error ? e.message : 'Unable to start payment.'); setBusy(false) }
  }
  return <section className="page"><header className="page-header"><div><span className="eyebrow">Cloud resource plans</span><h1>Plans & Billing</h1><p>Upgrade your database allocation using Razorpay test checkout.</p></div><Link className="secondary-button" to="/databases">My Databases</Link></header>
    <div className="learner-rank-banner"><strong>Razorpay test mode</strong><span>No real money is charged. Use Razorpay test payment details.</span></div>
    {error && <p className="error-state" role="alert">{error}</p>}{message && <p className="backup-message" role="status">{message}</p>}
    {data && history ? <><div className="billing-plan-grid"><article className="ops-card reward-card"><span className="eyebrow">Your current allocation</span><h2>{data.plan.name === 'PRO' ? 'Lab Pro' : 'Free learner'}</h2><strong>{data.plan.maximumDatabases} databases</strong><p>{formatBytes(data.plan.storagePerDatabaseBytes)} per database</p><p>{formatBytes(data.plan.storagePerDatabaseBytes * data.plan.maximumDatabases)} total available allocation</p></article><article className="ops-card reward-card"><h2>Lab Pro</h2><strong>₹199 one-time test payment</strong><p>At least 5 databases · 50 MB per database</p><p>Applies to existing and new workspaces after backend verification.</p><button className="primary-button" disabled={busy || !history.configured || data.plan.name === 'PRO'} onClick={() => void pay()}>{data.plan.name === 'PRO' ? 'Pro active' : busy ? 'Processing…' : 'Pay with Razorpay (test)'}</button>{!history.configured && <p>Test checkout is awaiting backend configuration.</p>}</article></div>
    <PaymentHistory report={history} busy={busy} onRefresh={id => void refreshOrder(id)} /></> : !error && <p>Loading your plan…</p>}
  </section>
}
