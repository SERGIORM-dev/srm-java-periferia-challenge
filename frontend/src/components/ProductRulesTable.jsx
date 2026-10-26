const PROVIDERS = { AUTO: 'Automático', PROVIDER_A: 'Proveedor A', PROVIDER_B: 'Proveedor B' };

export default function ProductRulesTable({ rules, loading, busy, onRefresh, onEdit, onDelete, onPrice }) {
  return (
    <section className="panel" aria-labelledby="rules-title">
      <div className="section-heading">
        <h2 id="rules-title">Productos</h2>
        <button type="button" onClick={onRefresh} disabled={loading || busy}>
          {loading ? 'Cargando…' : 'Actualizar lista'}
        </button>
      </div>
      <div className="table-scroll">
        <table>
          <caption className="sr-only">Reglas de productos registradas</caption>
          <thead><tr>
            <th scope="col">SKU</th><th scope="col">Nombre</th><th scope="col">Margen</th>
            <th scope="col">Preferencia</th><th scope="col">Estado</th><th scope="col">Acciones</th>
          </tr></thead>
          <tbody>
            {rules.map((rule) => <tr key={rule.id}>
              <th scope="row" className="sku">{rule.sku}</th>
              <td>{rule.name}</td><td>{rule.marginPercentage} %</td>
              <td>{PROVIDERS[rule.preferredProvider]}</td>
              <td><span className={`badge ${rule.active ? 'active' : 'inactive'}`}>{rule.active ? 'Activo' : 'Inactivo'}</span></td>
              <td><div className="actions row-actions">
                <button type="button" disabled={busy || loading} onClick={() => onEdit(rule)}>Editar</button>
                <button type="button" className="danger" disabled={busy || loading} onClick={() => onDelete(rule)}>Eliminar</button>
                <button type="button" disabled={busy || loading} onClick={() => onPrice(rule.sku)}>Consultar precio</button>
              </div></td>
            </tr>)}
            {!rules.length && <tr><td colSpan="6" className="empty">
              {loading ? 'Cargando productos…' : 'Sin reglas para mostrar.'}
            </td></tr>}
          </tbody>
        </table>
      </div>
    </section>
  );
}
