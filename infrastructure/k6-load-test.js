import http from 'k6/http';
import { check, group, sleep } from 'k6';

/**
 * Brightloop Phase 5 k6 Load & Latency Benchmark
 *
 * Usage:
 *   k6 run -e BASE_URL=http://localhost:8080 \
 *          -e ADMIN_EMAIL=admin@brightloop.local \
 *          -e ADMIN_PASSWORD='YourAdminPassword123!' \
 *          infrastructure/k6-load-test.js
 */
export const options = {
  stages: [
    { duration: '10s', target: 10 },
    { duration: '20s', target: 25 },
    { duration: '5s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
  },
};

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@brightloop.local';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'BrightloopAdmin!2026';

export function setup() {
  const jar = http.cookieJar();
  const csrfRes = http.get(`${BASE_URL}/api/v1/auth/csrf`);
  check(csrfRes, { 'csrf handshake 200': (r) => r.status === 200 });
  const csrfData = csrfRes.json('data');

  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
    {
      headers: {
        'Content-Type': 'application/json',
        [csrfData.headerName]: csrfData.token,
      },
    }
  );
  check(loginRes, { 'admin login 200': (r) => r.status === 200 });

  const cookies = jar.cookiesForURL(BASE_URL);
  return { sessionCookie: cookies.SESSION ? cookies.SESSION[0] : '' };
}

export default function (data) {
  const params = {
    headers: {
      Accept: 'application/json',
      Cookie: `SESSION=${data.sessionCookie}`,
    },
  };

  group('Executive Analytics & Financial Dashboards', () => {
    const overview = http.get(`${BASE_URL}/api/v1/analytics/overview`, params);
    check(overview, {
      'overview status 200': (r) => r.status === 200,
      'overview success true': (r) => r.json('success') === true,
    });

    const revenue = http.get(
      `${BASE_URL}/api/v1/analytics/revenue?granularity=MONTHLY`,
      params
    );
    check(revenue, { 'revenue status 200': (r) => r.status === 200 });

    const subsAnalytics = http.get(
      `${BASE_URL}/api/v1/analytics/subscriptions`,
      params
    );
    check(subsAnalytics, {
      'subscription analytics status 200': (r) => r.status === 200,
    });

    const receivables = http.get(
      `${BASE_URL}/api/v1/analytics/receivables`,
      params
    );
    check(receivables, { 'receivables status 200': (r) => r.status === 200 });

    const renewals = http.get(
      `${BASE_URL}/api/v1/analytics/renewals?windowDays=30`,
      params
    );
    check(renewals, { 'renewals status 200': (r) => r.status === 200 });
  });

  group('Paginated Operational Queries & CSV Export', () => {
    const subs = http.get(
      `${BASE_URL}/api/v1/subscriptions?page=0&size=20`,
      params
    );
    check(subs, { 'subscriptions list 200': (r) => r.status === 200 });

    const invoices = http.get(
      `${BASE_URL}/api/v1/invoices?page=0&size=20&outstanding=true`,
      params
    );
    check(invoices, { 'invoices list 200': (r) => r.status === 200 });

    const payments = http.get(
      `${BASE_URL}/api/v1/payments?page=0&size=20`,
      params
    );
    check(payments, { 'payments list 200': (r) => r.status === 200 });

    const csv = http.get(
      `${BASE_URL}/api/v1/analytics/reports/export?section=revenue`,
      params
    );
    check(csv, { 'csv export 200': (r) => r.status === 200 });
  });

  sleep(0.2);
}
