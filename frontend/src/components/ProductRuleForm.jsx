import { useState } from 'react';
import ErrorNotice from './ErrorNotice.jsx';

const PROVIDERS = [
  ['AUTO', 'Automático'],
  ['PROVIDER_A', 'Proveedor A'],
  ['PROVIDER_B', 'Proveedor B'],
];

export default function ProductRuleForm({ rule, busy, error, onSave, onCancel, onClearError }) {
  const [form, setForm] = useState({
    sku: rule?.sku || '',
    name: rule?.name || '',
    marginPercentage: String(rule?.marginPercentage ?? 10),
    preferredProvider: rule?.preferredProvider || 'AUTO',
    active: rule?.active ?? true,
  });

  function change(event) {
    const { name, value, type, checked } = event.target;
    setForm((current) => ({ ...current, [name]: type === 'checkbox' ? checked : value }));
    onClearError();
  }

  function submit(event) {
    event.preventDefault();
    const payload = {
      name: form.name,
      marginPercentage: Number(form.marginPercentage),
      preferredProvider: form.preferredProvider,
      active: form.active,
    };
    if (!rule) payload.sku = form.sku;
    onSave(payload);
  }

  function fieldError(name) {
    return error?.fields?.[name]
      ? <small className="field-error" id={`error-${name}`}>{error.fields[name]}</small>
      : null;
  }

  return (
    <section className="panel" aria-labelledby="rule-form-title" id="rule-form">
      <h2 id="rule-form-title">{rule ? `Editar ${rule.sku}` : 'Nueva regla'}</h2>
      <ErrorNotice error={error} />
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          <label htmlFor="rule-sku">SKU</label>
          <input id="rule-sku" name="sku" value={form.sku} onChange={change}
            maxLength={64} required disabled={Boolean(rule)}
            aria-invalid={Boolean(error?.fields?.sku)} aria-describedby="error-sku" />
          {fieldError('sku')}
          {rule && <small>El SKU se conserva al editar.</small>}

          <label htmlFor="rule-name">Nombre</label>
          <input id="rule-name" name="name" value={form.name} onChange={change}
            maxLength={150} required aria-invalid={Boolean(error?.fields?.name)}
            aria-describedby="error-name" />
          {fieldError('name')}

          <label htmlFor="rule-margin">Margen (%)</label>
          <input id="rule-margin" name="marginPercentage" type="number" value={form.marginPercentage}
            onChange={change} min="0" max="99999.99" step="0.01" required
            aria-invalid={Boolean(error?.fields?.marginPercentage)} aria-describedby="error-marginPercentage" />
          {fieldError('marginPercentage')}

          <label htmlFor="rule-provider">Proveedor preferido</label>
          <select id="rule-provider" name="preferredProvider" value={form.preferredProvider} onChange={change}>
            {PROVIDERS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>

          <label className="checkbox" htmlFor="rule-active">
            <input id="rule-active" name="active" type="checkbox" checked={form.active} onChange={change} />
            Producto activo
          </label>
          <div className="actions">
            <button className="primary" type="submit">{busy ? 'Guardando…' : rule ? 'Guardar cambios' : 'Crear regla'}</button>
            <button type="button" onClick={onCancel}>{rule ? 'Cancelar edición' : 'Limpiar'}</button>
          </div>
        </fieldset>
      </form>
    </section>
  );
}
