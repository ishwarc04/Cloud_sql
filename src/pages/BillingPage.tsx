import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiRequest } from '../app/api'
import { formatBytes } from '../features/databases/format'
interface Billing { plan: { name: string; maximumDatabases: number; storagePerDatabaseBytes: number }; payments: { requestId: string; amountPaise: number; status: string; createdAt: string }[]; demoOnly: boolean }
export function BillingPage() {
  const [data, setData] = useState<Billing | null>(null)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [checkout, setCheckout] = useState(false)
  const [busy, setBusy] = useState(false)
  const [requestId, setRequestId] = useState('')
  useEffect(() => { let active = true; apiRequest<Billing>('/api/billing').then(value => { if (active) setData(value) }).catch((e: unknown) => { if (active) setError(e instanceof Error ? e.message : 'Unable to load plan.') }); return () => { active = false } }, [])
  async function pay(failure: boolean) {
    setBusy(true); setError(''); setMessage('')
    try { const value = await apiRequest<Billing>('/api/billing/upgrade', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ requestId, simulateFailure: failure }) }); setData(value); setCheckout(false); setMessage(value.plan.name === 'PRO' ? 'Demo upgrade complete. Your existing databases now have the higher storage limit.' : 'Demo payment declined. Your plan and quotas were preserved.') }
    catch (e) { setError(e instanceof Error ? e.message : 'Payment simulation failed. Retry with the same request.') }
    finally { setBusy(false) }
  }
  return <section className="page"><header className="page-header"><div><span className="eyebrow">Cloud resources · pay as a service</span><h1>Plans & demo billing</h1><p>Try a service upgrade and see your database quota grow.</p></div><Link className="secondary-button" to="/databases">My Databases</Link></header>
    <div className="learner-rank-banner"><strong>Demo payments only</strong><span>No real charges, card details, UPI information, or payment provider. Prices are illustrative.</span></div>
    {error && <p className="error-state" role="alert">{error}</p>}{message && <p className="backup-message" role="status">{message}</p>}
    {data ? <><div className="billing-plan-grid"><article className="ops-card reward-card"><span className="eyebrow">Your current allocation</span><h2>{data.plan.name === 'PRO' ? 'Lab Pro' : 'Free learner'}</h2><strong>{data.plan.maximumDatabases} databases</strong><p>{formatBytes(data.plan.storagePerDatabaseBytes)} per database</p><p>{formatBytes(data.plan.storagePerDatabaseBytes * data.plan.maximumDatabases)} total available allocation</p></article><article className="ops-card reward-card"><span className="eyebrow">More room for experiments</span><h2>Lab Pro · demo</h2><strong>₹199 simulated one-time payment</strong><p>At least 5 databases · 50 MB per database</p><p>Applies to existing and new workspaces. Backups retain their separate limits.</p><button className="primary-button" disabled={busy || data.plan.name === 'PRO'} onClick={() => { setRequestId(crypto.randomUUID()); setCheckout(true); setError('') }}>{data.plan.name === 'PRO' ? 'Pro active' : 'Open demo checkout'}</button></article></div>
    {checkout && <section className="ops-card reward-card" aria-label="Demo checkout"><h2>Confirm demo upgrade</h2><p>Simulate ₹199. No real money moves. Successful simulation permanently unlocks the demo Pro plan for your account.</p><div className="backup-row-actions"><button className="primary-button" disabled={busy} onClick={() => void pay(false)}>{busy ? 'Processing…' : 'Simulate successful payment'}</button><button className="secondary-button" disabled={busy} onClick={() => void pay(true)}>Simulate declined payment</button><button className="secondary-button" disabled={busy} onClick={() => setCheckout(false)}>Cancel</button></div></section>}
    <section className="ops-card ops-section"><div className="ops-card-heading"><h2>Demo receipts</h2><span>Latest 10</span></div><div className="ops-table-scroll"><table className="ops-table"><thead><tr><th>Receipt</th><th>Simulated amount</th><th>Result</th><th>Date</th></tr></thead><tbody>{data.payments.map(payment => <tr key={payment.requestId}><td>{payment.requestId.slice(0, 8)}</td><td>₹{payment.amountPaise / 100}</td><td>{payment.status}</td><td>{new Date(payment.createdAt).toLocaleString()}</td></tr>)}</tbody></table>{data.payments.length === 0 && <p className="ops-empty">No simulated payments yet.</p>}</div></section></> : !error && <p>Loading your plan…</p>}
  </section>
}
