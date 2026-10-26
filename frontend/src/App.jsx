import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from './api.js';
import ErrorNotice from './components/ErrorNotice.jsx';
import ProductRuleForm from './components/ProductRuleForm.jsx';
import ProductRulesTable from './components/ProductRulesTable.jsx';
import PricePanel from './components/PricePanel.jsx';

function sorted(rules) {
  return [...rules].sort((a, b) => a.sku.localeCompare(b.sku));
}

export default function App() {
  const [rules, setRules] = useState([]);
  const [loading, setLoading] = useState(true);
  const [listError, setListError] = useState(null);
  const [formError, setFormError] = useState(null);
  const [actionError, setActionError] = useState(null);
  const [editing, setEditing] = useState(null);
  const [formVersion, setFormVersion] = useState(0);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [priceRequest, setPriceRequest] = useState(null);
  const [priceVersion, setPriceVersion] = useState(0);
  const listSequence = useRef(0);

  const loadRules = useCallback(async () => {
    const current = ++listSequence.current;
    setLoading(true);
    setListError(null);
    try {
      const values = await api.listRules();
      if (current === listSequence.current) setRules(sorted(values));
    } catch (error) {
      if (current === listSequence.current) setListError(error);
    } finally {
      if (current === listSequence.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRules();
    return () => { listSequence.current += 1; };
  }, [loadRules]);

  function clearPrice() {
    setPriceRequest(null);
    setPriceVersion((value) => value + 1);
  }

  function clearForm() {
    setEditing(null);
    setFormError(null);
    setFormVersion((value) => value + 1);
  }

  async function saveRule(payload) {
    if (busy) return;
    setBusy(true);
    setFormError(null);
    setActionError(null);
    setNotice('');
    try {
      const saved = editing ? await api.updateRule(editing.sku, payload) : await api.createRule(payload);
      setRules((current) => sorted([...current.filter((rule) => rule.id !== saved.id), saved]));
      setNotice(`Regla guardada: ${saved.sku}.`);
      clearForm();
      clearPrice();
    } catch (error) {
      setFormError(error);
    } finally {
      setBusy(false);
    }
  }

  async function deleteRule(rule) {
    if (busy || !window.confirm(`¿Eliminar la regla ${rule.sku}?`)) return;
    setBusy(true);
    setActionError(null);
    setNotice('');
    try {
      await api.deleteRule(rule.sku);
      setRules((current) => current.filter((item) => item.id !== rule.id));
      setNotice(`Regla eliminada: ${rule.sku}.`);
      if (editing?.sku === rule.sku) clearForm();
      clearPrice();
    } catch (error) {
      setActionError(error);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main>
      <header className="page-heading">
        <h1>Reglas de producto y precios</h1>
        <p>Administra las reglas y consulta el precio final de cada producto.</p>
      </header>
      {notice && <p className="notice success" role="status">{notice}</p>}
      <ErrorNotice error={listError} />
      <ErrorNotice error={actionError} />
      <ProductRulesTable rules={rules} loading={loading} busy={busy}
        onRefresh={() => { clearPrice(); loadRules(); }}
        onEdit={(rule) => { setEditing(rule); setFormError(null); setNotice(''); }}
        onDelete={deleteRule}
        onPrice={(sku) => setPriceRequest({ sku })} />
      <div className="workspace">
        <ProductRuleForm key={editing?.sku || `new-${formVersion}`} rule={editing}
          busy={busy || loading} error={formError} onSave={saveRule} onCancel={clearForm}
          onClearError={() => setFormError(null)} />
        <PricePanel key={priceVersion} request={priceRequest} />
      </div>
    </main>
  );
}
