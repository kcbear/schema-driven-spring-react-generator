import { expect, test } from '@playwright/test';

type PurchaseOrder = {
  id: number;
  orderNumber: string;
  supplier: string;
  orderDate?: string;
  totalAmount?: number;
  approved?: boolean;
};

test('generated UI creates, edits and deletes a purchase order', async ({ page }) => {
  const orders: PurchaseOrder[] = [];
  let nextId = 1;
  const requests: Array<{ method: string; body?: PurchaseOrder }> = [];

  await page.route('**/api/purchase-orders**', async route => {
    const request = route.request();
    const method = request.method();
    const url = new URL(request.url());
    const id = Number(url.pathname.split('/').at(-1));

    if (method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: orders,
          totalElements: orders.length,
          totalPages: 1,
          number: 0,
          size: 20
        })
      });
      return;
    }

    if (method === 'POST') {
      const body = request.postDataJSON() as PurchaseOrder;
      requests.push({ method, body });
      const created = { ...body, id: nextId++ };
      orders.push(created);
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(created) });
      return;
    }

    if (method === 'PUT') {
      const body = request.postDataJSON() as PurchaseOrder;
      requests.push({ method, body });
      const index = orders.findIndex(order => order.id === id);
      if (index < 0) {
        await route.fulfill({ status: 404, body: 'Not found' });
        return;
      }
      orders[index] = { ...body, id };
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(orders[index]) });
      return;
    }

    if (method === 'DELETE') {
      requests.push({ method });
      const index = orders.findIndex(order => order.id === id);
      if (index >= 0) orders.splice(index, 1);
      await route.fulfill({ status: 204, body: '' });
      return;
    }

    await route.fulfill({ status: 405, body: 'Method not allowed' });
  });

  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'PurchaseOrder', level: 1 })).toBeVisible();

  await page.getByLabel('Order number').fill('PO-1001');
  await page.getByLabel('Supplier').fill('Acme Supplies');
  await page.getByLabel('Order date').fill('2026-11-02');
  await page.getByLabel('Total amount').fill('1250.50');
  await page.getByLabel('Approved').check();
  await page.getByRole('button', { name: 'Save' }).click();

  const createdRow = page.getByRole('row').filter({ hasText: 'PO-1001' });
  await expect(createdRow).toBeVisible();
  expect(requests[0]).toMatchObject({
    method: 'POST',
    body: {
      orderNumber: 'PO-1001',
      supplier: 'Acme Supplies',
      orderDate: '2026-11-02',
      totalAmount: 1250.5,
      approved: true
    }
  });

  await createdRow.getByRole('button', { name: 'Edit' }).click();
  await expect(page.getByLabel('Approved')).toBeChecked();
  await page.getByLabel('Supplier').fill('Acme Industrial');
  await page.getByRole('button', { name: 'Save' }).click();

  const updatedRow = page.getByRole('row').filter({ hasText: 'PO-1001' });
  await expect(updatedRow).toContainText('Acme Industrial');
  expect(requests[1]).toMatchObject({ method: 'PUT', body: { supplier: 'Acme Industrial', id: 1 } });

  await updatedRow.getByRole('button', { name: 'Delete' }).click();
  await expect(page.getByRole('row').filter({ hasText: 'PO-1001' })).toHaveCount(0);
  expect(requests.map(request => request.method)).toEqual(['POST', 'PUT', 'DELETE']);
});

test('generated UI shows an API error accessibly', async ({ page }) => {
  await page.route('**/api/purchase-orders**', route =>
    route.fulfill({ status: 500, contentType: 'text/plain', body: 'Test service unavailable' })
  );

  await page.goto('/');
  await expect(page.getByRole('alert')).toContainText('Test service unavailable');
});
