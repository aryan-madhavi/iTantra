# Security Policy

AstraMesh / iTantra is a security-focused mesh messenger, and reports about its security are taken seriously. This page explains how to report vulnerabilities and defines our security scope.

## Reporting a Vulnerability

**Use GitHub's private vulnerability reporting** under the repository's Security tab.

Please do not open a public issue for anything that could put people at risk before a fix ships. AstraMesh is designed for high-risk network environments; a public proof-of-concept can be acted on before a patch reaches users.

A useful report states what an attacker can exploit, against which release or commit hash, and step-by-step reproduction instructions.

## Scope

In scope — core cryptographic and privacy guarantees:

- Confidentiality and integrity of private messages, media, and voice frames (Noise XX sessions over BLE, Double Ratchet, see `WHITEPAPER.md`).
- Identity: key handling, signature verification, impersonation resistance, session binding.
- Local persistence & panic wipe: key destruction and complete local data erasure.
- Metadata exposure beyond what the documentation discloses (see `PRIVACY_POLICY.md` and `docs/android/SECURITY_AND_CRYPTOGRAPHY.md`).
- Downgrade attacks: anything silently converting encrypted traffic to plaintext.

Out of scope — documented design properties:

- Public visibility of broadcast mesh announcements: nicknames and public keys are broadcast by design.
- Bluetooth physical proximity observability: passive BLE scanners can detect RF transmission in physical range.
- Mesh flooding/relay behavior inherent to ad-hoc mesh topologies.
- Denial of service requiring continuous physical RF jamming.

