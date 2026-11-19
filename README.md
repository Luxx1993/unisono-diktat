# Signaturschlüssel von diktat

Verschlüsselt mit AES-256 (PBKDF2, 600000 Runden). Das Passwort steht nur im Secret DIKTAT_SIGNING_PASSWORD.
Der Workflow .github/workflows/apk.yml entschlüsselt ihn zum Signieren. Diesen Zweig nicht löschen:
ohne den Schlüssel lässt sich die installierte App nicht mehr aktualisieren.
