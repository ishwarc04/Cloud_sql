import { useState, type FormEvent } from 'react'
import { adminService, type CreateQuestion } from './adminService'

type EditableTable = { name: string; columns: { name: string; type: string }[]; rows: string[][] }
const categories = ['Basic Select', 'Advanced Select', 'Aggregation', 'Basic Join', 'Advanced Join', 'Alternative Queries']
const columnTypes = ['INTEGER', 'DECIMAL', 'VARCHAR', 'DATE', 'BOOLEAN']
const emptyTable = (): EditableTable => ({ name: '', columns: [{ name: 'id', type: 'INTEGER' }, { name: 'name', type: 'VARCHAR' }], rows: [['1', ''], ['2', '']] })

export function AdminCreatePanel({ kind, onClose, onCreated }: { kind: 'user' | 'question'; onClose: () => void; onCreated: (message: string) => void }) {
  const [tables, setTables] = useState<EditableTable[]>([emptyTable()])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  function editTable(index: number, change: (table: EditableTable) => EditableTable) {
    setTables(current => current.map((table, i) => i === index ? change(table) : table))
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const value = (name: string) => String(form.get(name) ?? '').trim()
    setBusy(true); setError(null)
    try {
      if (kind === 'user') {
        const user = await adminService.createUser({ name: value('name'), email: value('email'), password: String(form.get('password')) })
        onCreated(`Learner account created for ${user.name}. They can sign in with the credentials you set.`)
      } else {
        const dataset: CreateQuestion['tables'] = tables.map(table => ({ name: table.name, columns: table.columns, sampleRows: table.rows.map(row => row.map((cell, i) => {
          if (cell === '') return null
          const type = table.columns[i].type
          if (type === 'INTEGER' || type === 'DECIMAL') {
            const number = Number(cell)
            if (!Number.isFinite(number) || (type === 'INTEGER' && !Number.isInteger(number))) throw new Error('Enter valid numbers in numeric columns.')
            return type === 'DECIMAL' ? cell : number
          }
          return type === 'BOOLEAN' ? cell === 'true' : cell
        })) }))
        const result = await adminService.createQuestion({ title: value('title'), difficulty: value('difficulty'), category: value('category'), description: value('description'), starterQuery: value('starterQuery'), solutionQuery: value('solutionQuery'), tables: dataset })
        onCreated(`“${result.title}” published. Learners can now find it in SQL Practice.`)
      }
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'Unable to save. Please retry.') }
    finally { setBusy(false) }
  }
  return <section className="ops-card ops-create-panel" aria-label={kind === 'user' ? 'Create learner account' : 'Add SQL question'}>
    <div className="ops-card-heading"><div><span className="eyebrow">{kind === 'user' ? 'Account management' : 'Catalogue management'}</span><h2>{kind === 'user' ? 'Create learner account' : 'Add SQL question'}</h2><p>{kind === 'user' ? 'Create a USER account without leaving your admin session.' : 'Write an original question, build its sample dataset, and validate the answer before publishing.'}</p></div><button className="secondary-button" type="button" disabled={busy} onClick={onClose}>Close</button></div>
    <form className="ops-create-form" onSubmit={event => void submit(event)}>
      <fieldset disabled={busy}>
        {kind === 'user' ? <><div className="ops-form-grid"><label>Full name<input name="name" required maxLength={80} autoComplete="off" placeholder="Learner’s name" /></label><label>Email<input type="email" name="email" required maxLength={254} autoComplete="off" placeholder="learner@example.com" /></label></div><label>Initial password<input type="password" name="password" required minLength={12} maxLength={128} autoComplete="new-password" /><small>12–128 characters. Share these credentials privately with the learner.</small></label><div className="ops-form-note"><span className="ops-badge">USER</span> New accounts always have learner permissions. Admins are provisioned through backend configuration.</div></> : <>
          <div className="ops-question-metadata"><label>Question title<input name="title" required maxLength={120} placeholder="A clear, original title" /></label><label>Difficulty<select name="difficulty">{['Easy', 'Medium', 'Hard'].map(value => <option key={value}>{value}</option>)}</select></label><label>Category<select name="category">{categories.map(value => <option key={value}>{value}</option>)}</select></label></div>
          <label>Problem statement<textarea name="description" required maxLength={8000} rows={4} placeholder="Explain the task, required output columns, and sorting. Use your own text and dataset." /></label>
          <div className="ops-dataset-heading"><div><h3>Sample dataset</h3><p>Lowercase table and column names. Blank cells become NULL. Dates use YYYY-MM-DD.</p></div><button className="secondary-button" type="button" disabled={tables.length >= 6} onClick={() => setTables(current => [...current, emptyTable()])}>+ Add table</button></div>
          {tables.map((table, ti) => <section className="ops-dataset-table" key={ti} aria-label={`Sample table ${ti + 1}`}>
            <div className="ops-dataset-toolbar"><label>Table name<input aria-label={`Table ${ti + 1} name`} required pattern="[a-z][a-z0-9_]{0,39}" value={table.name} placeholder="e.g. harbor_deliveries" onChange={event => editTable(ti, t => ({ ...t, name: event.target.value }))} /></label><div><button className="secondary-button" type="button" disabled={table.columns.length >= 12} onClick={() => editTable(ti, t => ({ ...t, columns: [...t.columns, { name: '', type: 'VARCHAR' }], rows: t.rows.map(row => [...row, '']) }))}>+ Column</button>{tables.length > 1 && <button className="ops-text-button" type="button" onClick={() => setTables(current => current.filter((_, i) => i !== ti))}>Remove table</button>}</div></div>
            <div className="ops-table-scroll"><table className="ops-dataset-grid"><thead><tr>{table.columns.map((column, ci) => <th key={ci}><input aria-label={`Table ${ti + 1} column ${ci + 1} name`} required pattern="[a-z][a-z0-9_]{0,39}" value={column.name} placeholder="column_name" onChange={event => editTable(ti, t => ({ ...t, columns: t.columns.map((c, i) => i === ci ? { ...c, name: event.target.value } : c) }))} /><div><select aria-label={`Table ${ti + 1} column ${ci + 1} type`} value={column.type} onChange={event => editTable(ti, t => ({ ...t, columns: t.columns.map((c, i) => i === ci ? { ...c, type: event.target.value } : c), rows: t.rows.map(row => row.map((cell, i) => i === ci ? '' : cell)) }))}>{columnTypes.map(type => <option key={type}>{type}</option>)}</select><button type="button" disabled={table.columns.length === 1} aria-label={`Remove column ${ci + 1} from table ${ti + 1}`} onClick={() => editTable(ti, t => ({ ...t, columns: t.columns.filter((_, i) => i !== ci), rows: t.rows.map(row => row.filter((_, i) => i !== ci)) }))}>×</button></div></th>)}<th /></tr></thead><tbody>{table.rows.map((row, ri) => <tr key={ri}>{row.map((cell, ci) => <td key={ci}>{table.columns[ci].type === 'BOOLEAN' ? <select aria-label={`Table ${ti + 1} row ${ri + 1} ${table.columns[ci].name}`} value={cell} onChange={event => editTable(ti, t => ({ ...t, rows: t.rows.map((r, i) => i === ri ? r.map((v, j) => j === ci ? event.target.value : v) : r) }))}><option value="">NULL</option><option>true</option><option>false</option></select> : <input aria-label={`Table ${ti + 1} row ${ri + 1} ${table.columns[ci].name}`} value={cell} maxLength={500} placeholder="NULL" onChange={event => editTable(ti, t => ({ ...t, rows: t.rows.map((r, i) => i === ri ? r.map((v, j) => j === ci ? event.target.value : v) : r) }))} />}</td>)}<td><button type="button" disabled={table.rows.length === 1} aria-label={`Remove row ${ri + 1} from table ${ti + 1}`} onClick={() => editTable(ti, t => ({ ...t, rows: t.rows.filter((_, i) => i !== ri) }))}>×</button></td></tr>)}</tbody></table></div>
            <button className="ops-text-button" type="button" disabled={table.rows.length >= 100} onClick={() => editTable(ti, t => ({ ...t, rows: [...t.rows, t.columns.map(() => '')] }))}>+ Add sample row</button>
          </section>)}
          <div className="ops-form-grid"><label>Learner starter query<textarea className="ops-sql-input" name="starterQuery" required maxLength={20000} rows={5} placeholder="SELECT * FROM harbor_deliveries;" /><small>A working SELECT that helps learners explore the data.</small></label><label>Reference answer<textarea className="ops-sql-input" name="solutionQuery" required maxLength={20000} rows={5} placeholder="SELECT ... FROM harbor_deliveries ORDER BY ...;" /><small>Validated on the sample data. Never exposed to learners.</small></label></div>
          <div className="ops-form-note">Easy earns 10 points · Medium 20 · Hard 30. Points are awarded only on the first successful solution.</div>
        </>}
        {error && <p className="error-state" role="alert">{error}</p>}
        <div className="ops-form-footer"><span>{kind === 'question' ? 'Both queries must be SELECT-only and return at most 200 rows.' : 'The learner can sign in normally after creation.'}</span><button className="primary-button" disabled={busy}>{busy ? 'Saving…' : kind === 'user' ? 'Create learner' : 'Validate and publish question'}</button></div>
      </fieldset>
    </form>
  </section>
}
