import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../api.js';
import ErrorNotice from './ErrorNotice.jsx';

function amount(value, currency, maximumFractionDigits = 2) {
  return new Intl.NumberFormat('es-CO', {
    style: 'currency', currency, currencyDisplay: 'code', minimumFractionDigits: 2, maximumFractionDigits,
  }).format(value);
}

export default function PricePanel({ request }) {
  const [sku, setSku] = useState('');
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const sequence = useRef(0);

  const consult = useCallback(async (value) => {
    const current = ++sequence.current;
    setLoading(true);
    setResult(null);
    setError(null);
    try {
      const price = await api.getPrice(value);
      if (current === sequence.current) setResult(price);
    } catch (failure) {
      if (current === sequence.current) setError(failure);
    } finally {
      if (current === sequence.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (request) {
      setSku(request.sku);
      consult(request.sku);
    }
  }, [request, consult]);

  useEffect(() => () => { sequence.current += 1; }, []);

  return (
    <section className="panel" aria-labelledby="price-title">
      <h2 id="price-title">Consultar precio</h2>
      <form className="price-query" onSubmit={(event) => { event.preventDefault(); consult(sku); }}>
        <label htmlFor="price-sku">SKU a consultar</label>
        <div className="actions">
          <input id="price-sku" value={sku} onChange={(event) => setSku(event.target.value)}
            maxLength={64} required disabled={loading} />
          <button className="primary" type="submit" disabled={loading}>
            {loading ? 'Consultando…' : 'Consultar'}
          </button>
        </div>
      </form>
      <ErrorNotice error={error} />
      {loading && <p role="status">Consultando precio…</p>}
      {!result && !error && !loading && <p className="muted">Selecciona un producto de la tabla o escribe su SKU.</p>}
      {result && <div className="price-result" aria-live="polite">
        <h3>{result.productName}</h3>
        <p className="sku">{result.sku}</p>
        <div className="actions">
          <span className={`badge source-${result.source.toLowerCase()}`}>{result.source}</span>
          <span className={`badge ${result.stale ? 'stale' : 'active'}`}>
            {result.stale ? 'Obsoleto / degradado' : 'Fresco'}
          </span>
        </div>
        <dl>
          <div><dt>Precio base</dt><dd>{amount(result.providerPrice, result.currency, 6)}</dd></div>
          <div><dt>Margen aplicado</dt><dd>{result.marginPercentage} %</dd></div>
          <div className="final-price"><dt>Precio final</dt><dd>{amount(result.finalPrice, result.currency)}</dd></div>
          <div><dt>Latencia</dt><dd>{result.responseTimeMs} ms</dd></div>
        </dl>
        {result.stale && <p className="notice warning">Se muestra el último precio disponible como respuesta degradada.</p>}
      </div>}
    </section>
  );
}
