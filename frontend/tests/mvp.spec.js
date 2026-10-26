import { test, expect } from '@playwright/test';

const pricePanel = (page) => page.getByRole('region', { name: 'Consultar precio', exact: true });
const row = (page, sku) => page.getByRole('row').filter({ has: page.getByRole('rowheader', { name: sku, exact: true }) });

test.beforeEach(async ({ page }) => {
  await page.goto('/');
  await expect(row(page, 'GAS-CORRIENTE')).toBeVisible();
});

test('crear, editar, desactivar, reactivar y eliminar una regla', async ({ page, request }) => {
  const sku = `UI-${Date.now()}`;
  try {
    await page.getByLabel('SKU', { exact: true }).fill(`  ${sku.toLowerCase()}  `);
    await page.getByLabel('Nombre', { exact: true }).fill('Producto de prueba');
    await page.getByLabel('Margen (%)', { exact: true }).fill('0');
    await page.getByRole('button', { name: 'Crear regla', exact: true }).click();
    await expect(row(page, sku)).toContainText('Producto de prueba');
    await row(page, sku).getByRole('button', { name: 'Editar', exact: true }).click();
    await expect(page.getByLabel('SKU', { exact: true })).toBeDisabled();
    await page.getByLabel('Nombre', { exact: true }).fill('Producto editado');
    await page.getByLabel('Margen (%)', { exact: true }).fill('150');
    await page.getByLabel('Proveedor preferido').selectOption('PROVIDER_B');
    await page.getByLabel('Producto activo').uncheck();
    await page.getByRole('button', { name: 'Guardar cambios', exact: true }).click();
    await expect(row(page, sku)).toContainText('Producto editado');
    await expect(row(page, sku)).toContainText('150 %');
    await expect(row(page, sku)).toContainText('Inactivo');
    await row(page, sku).getByRole('button', { name: 'Editar', exact: true }).click();
    await page.getByLabel('Producto activo').check();
    await page.getByRole('button', { name: 'Guardar cambios', exact: true }).click();
    await expect(row(page, sku).getByText('Activo', { exact: true })).toBeVisible();
    page.once('dialog', (dialog) => dialog.accept());
    await row(page, sku).getByRole('button', { name: 'Eliminar', exact: true }).click();
    await expect(row(page, sku)).toHaveCount(0);
  } finally {
    await request.delete(`/api/v1/product-rules/${sku}`);
  }
});

test('presenta duplicados y errores de validación sin perder el formulario', async ({ page }) => {
  await page.getByLabel('SKU', { exact: true }).fill('GAS-CORRIENTE');
  await page.getByLabel('Nombre', { exact: true }).fill('Duplicado');
  await page.getByRole('button', { name: 'Crear regla', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByLabel('Nombre', { exact: true })).toHaveValue('Duplicado');
  await page.getByLabel('SKU', { exact: true }).fill('UI-VALIDATION');
  await page.getByLabel('Nombre', { exact: true }).fill('   ');
  await page.getByRole('button', { name: 'Crear regla', exact: true }).click();
  await expect(page.getByLabel('Nombre', { exact: true })).toHaveAttribute('aria-invalid', 'true');
  await expect(page.locator('#error-name')).toBeVisible();
});

test('consulta precio real, muestra desglose y reutiliza la caché', async ({ page }) => {
  const panel = pricePanel(page);
  await row(page, 'GAS-CORRIENTE').getByRole('button', { name: 'Consultar precio', exact: true }).click();
  await expect(panel.getByText('Precio final', { exact: true })).toBeVisible();
  await expect(panel.getByText('10 %', { exact: true })).toBeVisible();
  await expect(panel.getByText('Fresco', { exact: true })).toBeVisible();
  await expect(panel.locator('dd').last()).toHaveText(/\d+ ms/);
  await panel.getByRole('button', { name: 'Consultar', exact: true }).click();
  await expect(panel.getByText('CACHE', { exact: true })).toBeVisible();
  await row(page, 'ADIT-LIMPIADOR').getByRole('button', { name: 'Consultar precio', exact: true }).click();
  await expect(panel.getByRole('alert')).toContainText(/inactiv/i);
  await expect(panel.getByText('Precio final', { exact: true })).toHaveCount(0);
  await panel.getByLabel('SKU a consultar').fill('NO-EXISTE');
  await panel.getByRole('button', { name: 'Consultar', exact: true }).click();
  await expect(panel.getByRole('alert')).toBeVisible();
});

test('un cambio de margen limpia el resultado anterior y se refleja en la próxima consulta', async ({ page, request }) => {
  const original = await (await request.get('/api/v1/product-rules')).json();
  const rule = original.find((value) => value.sku === 'GAS-CORRIENTE');
  try {
    await row(page, rule.sku).getByRole('button', { name: 'Consultar precio', exact: true }).click();
    await expect(pricePanel(page).getByText('Precio final', { exact: true })).toBeVisible();
    await row(page, rule.sku).getByRole('button', { name: 'Editar', exact: true }).click();
    await page.getByLabel('Margen (%)', { exact: true }).fill('20');
    await page.getByRole('button', { name: 'Guardar cambios', exact: true }).click();
    await expect(row(page, rule.sku)).toContainText('20 %');
    await expect(pricePanel(page).getByText('Precio final', { exact: true })).toHaveCount(0);
    await row(page, rule.sku).getByRole('button', { name: 'Consultar precio', exact: true }).click();
    await expect(pricePanel(page).getByText('20 %', { exact: true })).toBeVisible();
  } finally {
    await request.put(`/api/v1/product-rules/${rule.sku}`, { data: {
      name: rule.name, marginPercentage: rule.marginPercentage,
      preferredProvider: rule.preferredProvider, active: rule.active,
    } });
  }
});

test('distingue respaldo degradado y muestra el error si no hay precio disponible', async ({ page }) => {
  const panel = pricePanel(page);
  await page.route('**/api/v1/prices?*', (route) => route.fulfill({ json: {
    sku: 'GAS-CORRIENTE', productName: 'Gasolina corriente', providerPrice: 100,
    marginPercentage: 10, finalPrice: 110, currency: 'COP',
    source: 'FALLBACK_CACHE', stale: true, responseTimeMs: 850,
  } }));
  await panel.getByLabel('SKU a consultar').fill('GAS-CORRIENTE');
  await panel.getByRole('button', { name: 'Consultar', exact: true }).click();
  await expect(panel.getByText('FALLBACK_CACHE', { exact: true })).toBeVisible();
  await expect(panel.getByText('Obsoleto / degradado', { exact: true })).toBeVisible();
  await page.unroute('**/api/v1/prices?*');
  await page.route('**/api/v1/prices?*', (route) => route.fulfill({ status: 503, json: {
    title: 'Servicio no disponible', status: 503, detail: 'Ningún proveedor tiene un precio disponible.',
  } }));
  await panel.getByRole('button', { name: 'Consultar', exact: true }).click();
  await expect(panel.getByRole('alert')).toHaveText('Ningún proveedor tiene un precio disponible.');
  await expect(panel.getByText('Precio final', { exact: true })).toHaveCount(0);
  await page.unroute('**/api/v1/prices?*');
  await panel.getByRole('button', { name: 'Consultar', exact: true }).click();
  await expect(panel.getByText('Precio final', { exact: true })).toBeVisible();
});

test('la pantalla funciona en móvil y mantiene el desplazamiento dentro de la tabla', async ({ page }) => {
  await page.screenshot({ path: 'test-results/mvp-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('button', { name: 'Crear regla', exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: 'test-results/mvp-mobile.png', fullPage: true });
});
