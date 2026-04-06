# Phase 6: Security Hardening - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md -- this log preserves the alternatives considered.

**Date:** 2026-04-04
**Phase:** 06-security-hardening
**Areas discussed:** SSRF-Strategie, Path-Traversal-Scope

---

## SSRF-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| Blocklist (Empfohlen) | Private IPs, localhost, link-local blockieren. Alle externen HTTPS-URLs erlaubt. | ✓ |
| Allowlist | Nur explizit erlaubte Domains (z.B. gran-turismo.com). | |
| Blocklist + Allowlist | Blocklist als Basis, optionale Allowlist-Config. | |

**User's choice:** Blocklist (Empfohlen)
**Notes:** Einfach und ausreichend fuer Admin-Tool mit begrenzten Callern.

### Follow-up: DNS-Rebinding

| Option | Description | Selected |
|--------|-------------|----------|
| Nur Hostname-Check | Hostnamen wie localhost blockieren, private IPs als String-Check. | ✓ |
| DNS-Resolve + IP-Check | Hostname aufloesen, IP gegen private Ranges pruefen. | |

**User's choice:** Nur Hostname-Check
**Notes:** Kein DNS-Resolve noetig -- reicht fuer Admin-Tool.

---

## Path-Traversal-Scope

| Option | Description | Selected |
|--------|-------------|----------|
| store() + storeImage() | Nur die beiden im Concern identifizierten Methoden. | |
| Alle 3 Methoden | Auch storeFromUrl() absichern (defense-in-depth). | ✓ |

**User's choice:** Alle 3 Methoden
**Notes:** Defense-in-depth bevorzugt, auch wenn storeFromUrl() Pfade intern generiert.

---

## Claude's Discretion

- Exception-Typ fuer Path-Traversal-Rejection
- Private IP Range Matching Implementierung
- Ob Validierung in Helper-Methode extrahiert wird

## Deferred Ideas

None.
