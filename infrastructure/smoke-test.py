#!/usr/bin/env python3
"""Exercise a running Brightloop API and PostgreSQL through real HTTP sessions.

Requires only Python 3's standard library. Records are uniquely named and retained
for inspection. Never point this mutating development check at production.
"""

import http.cookiejar
import json
import os
import re
import secrets
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, timedelta
from decimal import Decimal


BASE_URL = os.environ.get("BASE_URL", "http://localhost:8080").rstrip("/")
MAILPIT_URL = os.environ.get("MAILPIT_URL", "http://localhost:8025").rstrip("/")
PREFIX = "/api/v1"
CHECKS = 0


def check(condition, message):
    global CHECKS
    if not condition:
        raise AssertionError(message)
    CHECKS += 1


class Client:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.csrf = None

    def request(self, method, path, body=None, expected=200, csrf=True, api=True):
        url = BASE_URL + (PREFIX if api else "") + path
        headers = {"Accept": "application/json"}
        if body is not None:
            headers["Content-Type"] = "application/json"
        if csrf and method not in {"GET", "HEAD", "OPTIONS"} and self.csrf:
            headers[self.csrf["headerName"]] = self.csrf["token"]
        request = urllib.request.Request(url, data=None if body is None else json.dumps(body).encode(),
                                         headers=headers, method=method)
        try:
            response = self.opener.open(request, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            status = response.status
            raw = response.read()
        allowed = (expected,) if isinstance(expected, int) else expected
        check(status in allowed, f"{method} {path}: expected {allowed}, received HTTP {status}")
        if not raw:
            check(status == 204, f"{method} {path}: unexpected empty response")
            return None
        try:
            payload = json.loads(raw)
        except ValueError as error:
            raise AssertionError(f"{method} {path}: response must be JSON") from error
        if not api:
            return payload
        keys = {"success", "statusCode", "status", "message", "timestamp"}
        check(keys <= payload.keys(), f"{method} {path}: incomplete response envelope")
        check(payload["statusCode"] == status, f"{method} {path}: envelope status mismatch")
        check(payload["success"] is (status < 400), f"{method} {path}: success flag mismatch")
        if status >= 400:
            check({"errorCode", "path", "details"} <= payload.keys(),
                  f"{method} {path}: incomplete error envelope")
            check(bool(payload["errorCode"]) and bool(payload["message"]),
                  f"{method} {path}: missing actionable error")
            check("stackTrace" not in payload and "exception" not in payload,
                  f"{method} {path}: internal exception exposed")
            return payload
        check("data" in payload, f"{method} {path}: missing data envelope")
        return payload["data"]

    def refresh_csrf(self):
        self.csrf = self.request("GET", "/auth/csrf")
        check(bool(self.csrf.get("token")) and bool(self.csrf.get("headerName")), "CSRF token shape")

    def login(self, email, password):
        self.refresh_csrf()
        self.request("POST", "/auth/login", {"email": email, "password": password})
        self.refresh_csrf()
        return self.request("GET", "/auth/me")


def page_contains(page, record_id):
    check({"content", "totalElements", "totalPages", "number", "size"} <= page.keys(), "Pagination shape")
    check(any(item["id"] == record_id for item in page["content"]), "Expected record absent from filtered list")


def reset_token_for(email):
    """Read only this run's recipient; do not delete or print other mailbox data."""
    for _ in range(40):
        with urllib.request.urlopen(MAILPIT_URL + "/api/v1/messages?limit=200", timeout=10) as response:
            messages = json.load(response).get("messages", [])
        for message in messages:
            if not any(recipient.get("Address", "").lower() == email.lower()
                       for recipient in message.get("To", [])):
                continue
            identifier = urllib.parse.quote(message["ID"], safe="")
            with urllib.request.urlopen(MAILPIT_URL + "/api/v1/message/" + identifier, timeout=10) as response:
                content = json.load(response)
            text = content.get("Text", "") + " " + content.get("HTML", "")
            match = re.search(r"[?&]token=([A-Za-z0-9_-]+)", text)
            if match:
                return match.group(1)
        time.sleep(0.25)
    raise AssertionError("Password reset email with a token link did not arrive in Mailpit")


def main():
    admin_email = os.environ.get("SMOKE_ADMIN_EMAIL")
    admin_password = os.environ.get("SMOKE_ADMIN_PASSWORD")
    if not admin_email or not admin_password:
        raise AssertionError("Set SMOKE_ADMIN_EMAIL and SMOKE_ADMIN_PASSWORD to an existing bootstrap admin")
    suffix = secrets.token_hex(6)
    password = "Smoke!Aa9" + secrets.token_urlsafe(18)
    email = f"smoke-customer-{suffix}@example.test"
    second_email = f"smoke-other-{suffix}@example.test"
    staff_email = f"smoke-staff-{suffix}@example.test"
    customer = Client()
    other = Client()
    admin = Client()
    staff = Client()
    anonymous = Client()
    health = anonymous.request("GET", "/actuator/health", api=False)
    check(health["status"] == "UP", "Actuator health must be UP")
    anonymous.request("GET", "/auth/me", expected=401)
    anonymous.request("GET", "/products", expected=401)
    anonymous.request("POST", "/auth/register", {"name": "CSRF check", "email": email, "password": password},
                      expected=403, csrf=False)
    customer.refresh_csrf()
    customer.request("POST", "/auth/register", {"name": "Weak", "email": email, "password": "weak"}, expected=400)
    registration = {"name": f"Smoke customer {suffix}", "email": email, "password": password}
    customer.request("POST", "/auth/register", registration, expected=201)
    customer.request("POST", "/auth/register", {**registration, "email": email.upper()}, expected=409)
    other.refresh_csrf()
    other.request("POST", "/auth/register", {"name": f"Smoke other {suffix}", "email": second_email,
                                           "password": password}, expected=201)
    long_email = f"smoke-long-{suffix}@{'x' * 40}.{'y' * 40}.example.test"
    long_customer = Client()
    long_customer.refresh_csrf()
    long_customer.request("POST", "/auth/register", {"name": f"Smoke long email {suffix}", "email": long_email,
                                                    "password": password}, expected=201)
    check(long_customer.login(long_email, password)["email"] == long_email, "Long email session persists")
    anonymous.refresh_csrf()
    anonymous.request("POST", "/auth/login", {"email": email, "password": "Incorrect!Aa9"}, expected=401)
    me = customer.login(email, password)
    other.login(second_email, password)
    check(me["role"] == "CUSTOMER", "Registration must create CUSTOMER role")
    admin_me = admin.login(admin_email, admin_password)
    check(admin_me["role"] == "ADMIN", "Provided admin account has ADMIN role")
    admin.request("POST", "/staff", {"name": "CSRF rejected", "email": staff_email, "password": password},
                  expected=403, csrf=False)
    check(any(cookie.name in {"SESSION", "JSESSIONID"} and cookie.has_nonstandard_attr("HttpOnly") for cookie in admin.jar),
          "Session cookie must be HttpOnly")
    print("PASS health, response envelopes, CSRF, registration and authenticated sessions")

    profile = customer.request("GET", "/customers/me")
    check(profile["userId"] == me["id"], "Customer profile retains linked user identity")
    own_id = profile["id"]
    other_id = other.request("GET", "/customers/me")["id"]
    customer_body = {"name": f"Smoke customer {suffix}", "email": email, "phone": "+1 202 555 0100",
                     "companyName": "Smoke company", "billingAddress": "1 Test Road", "status": "ACTIVE"}
    customer.request("PUT", "/customers/me", customer_body)
    check(customer.request("GET", "/customers/me")["phone"] == customer_body["phone"], "Profile persists")
    customer.request("GET", f"/customers/{other_id}", expected=(403, 404))
    customer.request("GET", "/customers", expected=403)
    customer.request("POST", "/customers", customer_body, expected=403)
    external = {**customer_body, "name": f"Smoke managed {suffix}", "email": f"managed-{suffix}@example.test"}
    managed = admin.request("POST", "/customers", external, expected=201)
    admin.request("PUT", f"/customers/{managed['id']}", {**external, "companyName": "Updated company"})
    check(admin.request("GET", f"/customers/{managed['id']}")["companyName"] == "Updated company", "Customer update persists")
    page_contains(admin.request("GET", "/customers?" + urllib.parse.urlencode({"q": suffix, "status": "ACTIVE"})), own_id)
    check(admin.request("GET", "/customers?q=absent-" + suffix)["totalElements"] == 0, "Customer search is applied")
    staff_body = {"name": f"Smoke staff {suffix}", "email": staff_email, "password": password}
    staff_user = admin.request("POST", "/staff", staff_body, expected=201)
    customer.request("POST", "/staff", staff_body, expected=403)
    staff_me = staff.login(staff_email, password)
    check(staff_me["role"] == "STAFF", "Created staff role")
    staff.request("POST", "/staff", {**staff_body, "email": f"blocked-{suffix}@example.test"}, expected=403)
    staff.request("GET", "/customers")
    staff.request("GET", "/users", expected=403)
    staff.request("PATCH", f"/users/{staff_user['id']}/role", {"role": "ADMIN"}, expected=403)
    customer.request("PATCH", f"/users/{staff_user['id']}/role", {"role": "ADMIN"}, expected=403)
    admin.request("PATCH", f"/users/{staff_user['id']}/role", {"role": "CUSTOMER"})
    admin.request("PATCH", f"/users/{staff_user['id']}/role", {"role": "STAFF"})
    staff.request("GET", "/auth/me", expected=401)
    staff.login(staff_email, password)
    page_contains(admin.request("GET", "/users?" + urllib.parse.urlencode({"q": suffix})), staff_user["id"])
    print("PASS customer persistence/isolation, staff creation and admin-only role management")

    product_body = {"name": f"Smoke product {suffix}", "type": "SERVICE", "salesPrice": "100.00", "costPrice": "40.00",
                    "notes": "HTTP integration fixture", "status": "ACTIVE",
                    "variants": [{"name": "Odoo", "priceAdjustment": "5.60"}]}
    customer.request("POST", "/products", product_body, expected=403)
    staff.request("POST", "/products", product_body, expected=403)
    admin.request("POST", "/products", {**product_body, "salesPrice": "-1.00"}, expected=(400, 422))
    admin.request("POST", "/products", {**product_body, "salesPrice": "1.001"}, expected=(400, 422))
    product = admin.request("POST", "/products", product_body, expected=201)
    admin.request("GET", "/products?size=101", expected=400)
    admin.request("GET", "/products?page=-1", expected=400)
    product_id = product["id"]
    variant_id = product["variants"][0]["id"]
    unrelated_product = admin.request("POST", "/products", {**product_body, "name": f"Unrelated {suffix}"}, expected=201)
    check(Decimal(str(product["salesPrice"])) == Decimal("100.00"), "Product base price")
    updated_product = {**product_body, "notes": "Updated over HTTP",
                       "variants": [{"id": variant_id, "name": "Odoo", "priceAdjustment": "5.60"}]}
    admin.request("PUT", f"/products/{product_id}", updated_product)
    check(customer.request("GET", f"/products/{product_id}")["notes"] == "Updated over HTTP", "Product update persists")
    page_contains(customer.request("GET", "/products?" + urllib.parse.urlencode({"q": suffix, "status": "ACTIVE"})), product_id)
    today = date.today()
    plan_body = {"name": f"Smoke monthly {suffix}", "description": "HTTP integration fixture", "price": "25.00",
                 "billingPeriod": "MONTHLY", "minimumQuantity": 1, "availableFrom": (today - timedelta(days=1)).isoformat(),
                 "availableUntil": (today + timedelta(days=400)).isoformat(), "automaticClosing": True,
                 "earlyClosingAllowed": False, "pausingAllowed": False, "renewalAllowed": True,
                 "status": "ACTIVE", "productIds": [product_id]}
    staff.request("POST", "/plans", plan_body, expected=403)
    customer.request("POST", "/plans", plan_body, expected=403)
    admin.request("POST", "/plans", {**plan_body, "minimumQuantity": 0}, expected=(400, 422))
    admin.request("POST", "/plans", {**plan_body, "availableUntil": (today - timedelta(days=2)).isoformat()}, expected=(400, 422))
    plan = admin.request("POST", "/plans", plan_body, expected=201)
    plan_id = plan["id"]
    admin.request("PUT", f"/plans/{plan_id}", {**plan_body, "description": "Updated plan"})
    plan = customer.request("GET", f"/plans/{plan_id}")
    check(plan["description"] == "Updated plan" and product_id in plan["productIds"], "Plan update and product association persist")
    page_contains(customer.request("GET", "/plans?" + urllib.parse.urlencode({"q": suffix, "status": "ACTIVE"})), plan_id)
    print("PASS products/variants, catalog permissions, money validation and associated plans")

    subscription_body = {"customerId": own_id, "planId": plan_id, "startDate": today.isoformat(),
                         "expiryDate": (today + timedelta(days=30)).isoformat(), "paymentTerms": "Net 15",
                         "lines": [{"productId": product_id, "variantId": variant_id, "quantity": 2, "taxRate": "10.00"}]}
    customer.request("POST", "/subscriptions", {**subscription_body, "customerId": other_id}, expected=(403, 404))
    customer.request("POST", "/subscriptions", {**subscription_body, "lines": [{"productId": product_id, "quantity": 0,
                                                                               "taxRate": "10.00"}]}, expected=(400, 422))
    customer.request("POST", "/subscriptions", {**subscription_body, "expiryDate": (today - timedelta(days=1)).isoformat()}, expected=(400, 422))
    customer.request("POST", "/subscriptions", {**subscription_body, "lines": [{"productId": unrelated_product["id"],
                                                                               "quantity": 1, "taxRate": "0.00"}]}, expected=(400, 422))
    customer.request("POST", "/subscriptions", {**subscription_body, "lines": [{"productId": product_id,
                         "variantId": unrelated_product["variants"][0]["id"], "quantity": 1, "taxRate": "0.00"}]}, expected=(400, 422))
    # Injecting a client price cannot override the server-derived snapshot.
    subscription = customer.request("POST", "/subscriptions", {**subscription_body,
                   "lines": [{**subscription_body["lines"][0], "unitPrice": "0.01"}]}, expected=(201, 400))
    if subscription.get("success") is False:
        subscription = customer.request("POST", "/subscriptions", subscription_body, expected=201)
    subscription_id = subscription["id"]
    check(subscription["state"] == "DRAFT" and bool(subscription["number"]), "New subscription is numbered DRAFT")
    check(Decimal(str(subscription["lines"][0]["unitPrice"])) == Decimal("105.60"), "Variant adjustment snapshot")
    check(Decimal(str(subscription["lines"][0]["amount"])) == Decimal("232.32"), "Line quantity plus tax calculation")
    check(Decimal(str(subscription["total"])) == Decimal("232.32"), "Subscription total calculation")
    customer.request("PUT", f"/subscriptions/{subscription_id}", {**subscription_body, "paymentTerms": "Net 30"})
    fetched = customer.request("GET", f"/subscriptions/{subscription_id}")
    check(fetched["paymentTerms"] == "Net 30", "Draft edit persists")
    check(fetched["customerId"] == own_id and fetched["planId"] == plan_id and bool(fetched["customerName"]),
          "Subscription detail retains relationship identities and customer name")
    check(fetched["lines"][0]["productId"] == product_id and fetched["lines"][0]["variantId"] == variant_id,
          "Subscription detail retains product and variant identities")
    page_contains(customer.request("GET", "/subscriptions?" + urllib.parse.urlencode({"q": subscription["number"], "state": "DRAFT"})), subscription_id)
    other.request("GET", f"/subscriptions/{subscription_id}", expected=(403, 404))
    other.request("PUT", f"/subscriptions/{subscription_id}", subscription_body, expected=(403, 404))
    check(all(item["customerId"] == other_id for item in other.request("GET", "/subscriptions")["content"]), "Customer list ownership")
    customer.request("POST", f"/subscriptions/{subscription_id}/transition", {"state": "QUOTATION"}, expected=403)
    staff.request("POST", f"/subscriptions/{subscription_id}/transition", {"state": "ACTIVE"}, expected=(409, 422))
    # A catalog change must not rewrite historical prices.
    admin.request("PUT", f"/products/{product_id}", {**updated_product, "salesPrice": "200.00"})
    snapshot = customer.request("GET", f"/subscriptions/{subscription_id}")
    check(Decimal(str(snapshot["lines"][0]["unitPrice"])) == Decimal("105.60"), "Catalog edit preserves subscription snapshot")
    for state in ("QUOTATION", "CONFIRMED", "ACTIVE", "CLOSED"):
        transitioned = staff.request("POST", f"/subscriptions/{subscription_id}/transition", {"state": state})
        check(transitioned["state"] == state, f"Transition to {state} persists")
    staff.request("POST", f"/subscriptions/{subscription_id}/transition", {"state": "ACTIVE"}, expected=(409, 422))
    admin.request("PUT", f"/subscriptions/{subscription_id}", subscription_body, expected=(409, 422))
    admin.request("DELETE", f"/products/{product_id}", expected=409)
    admin.request("DELETE", f"/plans/{plan_id}", expected=409)
    disposable_product = admin.request("POST", "/products", {**product_body, "name": f"Disposable {suffix}"}, expected=201)
    disposable_plan = admin.request("POST", "/plans", {**plan_body, "name": f"Disposable plan {suffix}",
                                                     "productIds": [disposable_product["id"]]}, expected=201)
    admin.request("DELETE", f"/plans/{disposable_plan['id']}", expected=(200, 204))
    admin.request("GET", f"/plans/{disposable_plan['id']}", expected=404)
    admin.request("DELETE", f"/products/{disposable_product['id']}", expected=(200, 204))
    admin.request("GET", f"/products/{disposable_product['id']}", expected=404)
    admin.request("DELETE", f"/products/{unrelated_product['id']}", expected=(200, 204))
    print("PASS draft editing, calculated snapshots, ownership, sequential lifecycle and deletion constraints")

    customer.request("POST", "/auth/forgot-password", {"email": email})
    token = reset_token_for(email)
    new_password = "Reset!Aa9" + secrets.token_urlsafe(18)
    anonymous.refresh_csrf()
    anonymous.request("POST", "/auth/reset-password", {"token": "invalid-token", "password": new_password}, expected=(400, 409, 422))
    anonymous.request("POST", "/auth/reset-password", {"token": token, "password": new_password})
    customer.request("GET", "/auth/me", expected=401)
    anonymous.request("POST", "/auth/reset-password", {"token": token, "password": new_password}, expected=(400, 409, 422))
    anonymous.request("POST", "/auth/login", {"email": email, "password": password}, expected=401)
    check(anonymous.login(email, new_password)["id"] == me["id"], "Reset password authenticates same account")
    anonymous.request("POST", "/auth/logout", expected=(200, 204))
    anonymous.request("GET", "/auth/me", expected=401)
    print("PASS SMTP reset link delivery, password replacement, single-use token and logout")
    print(f"PASS {CHECKS} assertions; retained fixture suffix: {suffix}; subscription ID: {subscription_id}")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, urllib.error.URLError, TimeoutError, KeyError, ValueError) as error:
        # No response payload, password, email reset link or token is logged.
        print(f"FAIL after {CHECKS} assertions: {error}", file=sys.stderr)
        sys.exit(1)
