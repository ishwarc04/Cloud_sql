import { useEffect, useState } from 'react'
import { apiRequest } from '../../app/api'
import { PaymentHistory, type PaymentReport } from '../billing/PaymentHistory'
export function AdminPaymentsPanel({ revision }: { revision: number }) {
  const [report, setReport] = useState<PaymentReport | null>(null)
  const [error, setError] = useState('')
  useEffect(() => { let active = true; apiRequest<PaymentReport>('/api/admin/payments').then(value => { if (active) { setReport(value); setError('') } }).catch((e: unknown) => { if (active) setError(e instanceof Error ? e.message : 'Unable to load payments.') }); return () => { active = false } }, [revision])
  return <>{error && <p className="error-state" role="alert">{error}</p>}{report ? <PaymentHistory report={report} admin /> : !error && <p>Loading payment records…</p>}</>
}
