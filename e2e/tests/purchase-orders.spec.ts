import { expect, test } from '@playwright/test';

const initialOrders = {
  content: [{ id: 1, orderNumber: 'PO-100', supplier: 'Acme', orderDate: '2026-11-01', totalAmount: 125.5, approved: false }],
  totalElements: 1, totalPages: 1, number: 0, size: 20
};

test('creates, edits and deletes a purchase order', async ({ page }) => {
  let orders = [...initialOrders.content];
  let nextId = 2;
  await page.route('**/api/purchase-orders**', async route => {
    const request = route.request();
    if (request.method() === 'GET') {
      await route.fulfill({ json: { ...initialOrders, content: orders, totalElements: orders.length } });
    } else if (request.method() === 'POST') {
      const created = { id: nextId++, ...request.postDataJSON() };
      orders.push(created);
      await route.fulfill({ status: 201, json: created });
    } else if (request.method() === 'PUT') {
      const id = Number(new URL(request.url()).pathname.split('/').pop());
      orders = orders.map(order => order.id === id ? { ...request.postDataJSON(), id } : order);
      await route.fulfill({ json: orders.find(order => order.id === id) });
    } else if (request.method() === 'DELETE') {
      const id = Number(new URL(request.url()).pathname.split('/').pop());
      orders = orders.filter(order => order.id !== id);
      await route.fulfill({ status: 204, body: '' });
    } else {
      await route.continue();
    }
  });

  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'PurchaseOrder' })).toBeVisible();
  await expect(page.getByText('PO-100')).toBeVisible();
  await page.getByLabel('Order number').fill('PO-200');
  await page.getByLabel('Supplier').fill('Globex');
  await page.getByLabel('Order date').fill('2026-11-02');
  await page.getByLabel('Total amount').fill('250.75');
  await page.getByLabel('Approved').check();
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('PO-200')).toBeVisible();
  await expect(page.getByText('Globex')).toBeVisible();

  const createdRow = page.getByRole('row').filter({ hasText: 'PO-200' });
  await createdRow.getByRole('button', { name: 'Edit' }).click();
  await expect(page.getByLabel('Approved')).toBeChecked();
  await page.getByLabel('Supplier').fill('Initech');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Initech')).toBeVisible();

  const editedRow = page.getByRole('row').filter({ hasText: 'PO-200' });
  await editedRow.getByRole('button', { name: 'Delete' }).click();
  await expect(page.getByText('PO-200')).toHaveCount(0);
});

test('shows an accessible error when the list API fails', async ({ page }) => {
  await page.route('**/api/purchase-orders**', route =>
    route.fulfill({ status: 500, body: 'Purchase orders unavailable' })
  );
  await page.goto('/');
  await expect(page.getByRole('alert')).toContainText('Purchase orders unavailable');
});
