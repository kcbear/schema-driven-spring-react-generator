import { expect, test } from '@playwright/test';

function pagePayload(page: number) {
  const start = page * 50 + 1;
  return {
    content: Array.from({ length: 50 }, (_, offset) => {
      const n = start + offset;
      return {
        id: n, symbol: `GLB${String(n).padStart(7, '0')}`,
        name: `Global Instrument ${n}`, instrumentType: n % 4 === 0 ? 'ETF' : 'STOCK',
        exchange: n % 2 === 0 ? 'NASDAQ' : 'ASX', country: n % 2 === 0 ? 'United States' : 'Australia',
        currency: n % 2 === 0 ? 'USD' : 'AUD', isin: `US${String(n).padStart(10, '0')}`,
        sector: 'Technology', lastPrice: 123.45, marketCap: 1000000000, volume: 100000, active: true
      };
    }),
    totalElements: 10000, totalPages: 200, number: page, size: 50
  };
}

test('renders a 10,000-instrument universe using server-side paging', async ({ page }) => {
  const requestedPages: number[] = [];
  await page.route('**/api/instruments**', async route => {
    const url = new URL(route.request().url());
    const pageNumber = Number(url.searchParams.get('page') ?? 0);
    requestedPages.push(pageNumber);
    await route.fulfill({ json: pagePayload(pageNumber) });
  });

  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Instrument' })).toBeVisible();
  await expect(page.getByText('GLB0000001')).toBeVisible();
  await expect(page.getByRole('row')).toHaveCount(51);
  await expect(page.getByText('Page 1 of 200 · 10000 instruments')).toBeVisible();
  await expect(page.getByText('Global Instrument 51')).toHaveCount(0);
  await page.getByRole('button', { name: 'Next' }).click();
  await expect(page.getByText('GLB0000051')).toBeVisible();
  await expect(page.getByText('Page 2 of 200 · 10000 instruments')).toBeVisible();
  expect(requestedPages).toEqual([0, 1]);
});

test('shows an accessible error when the instruments API fails', async ({ page }) => {
  await page.route('**/api/instruments**', route =>
    route.fulfill({ status: 500, body: 'Instrument catalogue unavailable' })
  );
  await page.goto('/');
  await expect(page.getByRole('alert')).toContainText('Instrument catalogue unavailable', { timeout: 15000 });
});
