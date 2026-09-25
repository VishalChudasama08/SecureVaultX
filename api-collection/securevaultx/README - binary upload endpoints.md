# Binary-body endpoints (not included as .yml requests)

Three endpoints take the raw file as the entire request body
(`application/octet-stream`, not multipart) plus an `X-Filename` header
with the *percent-encoded* original name:

```
POST /api/vault/files      Store in Secure Vault
POST /api/files/encrypt    Encrypt & Download
POST /api/files/decrypt    Decrypt an .enc you previously received
```

Bruno's binary-body support varies by version, so rather than ship a
`.yml` that might not load correctly in yours, use `curl` (or add these as
"binary file" body requests yourself in the Bruno UI — set the URL, method
POST, body type "File / Binary", pick a file, and add the two headers
below):

```bash
# Log in first and capture the session cookie + a CSRF token, then:

curl -X POST http://localhost:8080/api/vault/files \
  -H "Cookie: JSESSIONID=<your session cookie>" \
  -H "X-CSRF-TOKEN: <token from /api/auth/csrf>" \
  -H "X-Filename: $(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))" "report.pdf")" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @report.pdf

curl -X POST http://localhost:8080/api/files/encrypt \
  -H "Cookie: JSESSIONID=<your session cookie>" \
  -H "X-CSRF-TOKEN: <token from /api/auth/csrf>" \
  -H "X-Filename: report.pdf" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @report.pdf \
  --output report.pdf.enc

curl -X POST http://localhost:8080/api/files/decrypt \
  -H "Cookie: JSESSIONID=<your session cookie>" \
  -H "X-CSRF-TOKEN: <token from /api/auth/csrf>" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @report.pdf.enc \
  --output report-restored.pdf
```
