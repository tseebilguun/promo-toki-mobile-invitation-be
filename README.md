# promo-toki-mobile-invitation-be

Backend service for **Toki Mobile referral invitation campaign**.

Built with **Quarkus** and **jOOQ** (PostgreSQL). It provides REST APIs to:

- **Login** and receive a JWT token
- **Get referral info** (invitations + counts + entitlement status)
- **Send referral invitation** to a receiver MSISDN (push notification + SMS)
- **Get general info (DSD)** by providing msisdn/tokiId as query params (no JWT)

---

## Base URL

All examples below use:

- `http://10.21.68.222:6989`

---

## Exposed APIs (from `Resources.java`)

- `POST /login`
- `GET /getInfo` (JWT required)
- `GET /getGeneralInfo` (for DSD, query params)
- `POST /sendInvite` (JWT required)

---

## Authentication

- Call `POST /login` to get a JWT token (returned as a string in `data`)
- Pass JWT to protected endpoints using:

`Authorization: Bearer <JWT_TOKEN>`

`GET /getInfo` and `POST /sendInvite` read user info from JWT context:
- `jwt.msisdn`
- `jwt.tokiId`

---

## Main Logic (High level)

### 1) Send Invite (`POST /sendInvite`)
- Validates receiver msisdn (must be 8 digits)
- Rejects if receiver already has an invitation with status `SENT` or `ACCEPTED`
- Fetches receiver + sender Toki info from external Toki services
- Inserts into `REFERRAL_INVITATIONS` with `STATUS='SENT'`
    - `SENT_AT` default: `NOW()`
    - `EXPIRES_AT` default: `NOW() + 72 hours`
- Sends:
    - Push notification (Toki General API)
    - SMS (legacy SMS service)

### 2) Get Info (`GET /getInfo`)
- Fetches all invitations sent by the logged-in user
- Builds response:
    - `referrals`: invitation list (receiver msisdn, status, operator, expiresAt)
    - `successReferralsCount`: number of `ACCEPTED`
    - Entitlement:
        - Fetch latest row from `PROMOTION_ENTITLEMENTS` by `END_AT desc` for (tokiId, msisdn)
        - If `END_AT > now` → `hasActiveEntitlement=true`
        - Else → `hasActiveEntitlement=false`
        - `entitlementExpirationDate` always returns latest `END_AT` if exists (even if expired)

### 3) Get General Info (`GET /getGeneralInfo`) (DSD)
- Same type of info as `/getInfo`, but msisdn/tokiId are passed as query parameters
- Intended for DSD usage (does not depend on JWT context)

---

## cURL Examples

### 1) Login
**POST** `/login`

```sh
curl -X POST "http://10.21.68.222:6989/login" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "msisdn": "88112233",
    "tokiId": "TOKI_ID_123"
  }'
```

Example response (shape):
```json
{
  "result": "success",
  "message": "Login successful",
  "data": "<JWT_TOKEN_STRING>"
}
```

---

### 2) Get Info (JWT)
**GET** `/getInfo`

```sh
curl -X GET "http://10.21.68.222:6989/getInfo" \
  -H "Authorization: Bearer <JWT_TOKEN_STRING>" \
  -H "Accept: application/json"
```

Example response (shape):
```json
{
  "result": "Success",
  "message": "Fetched existing records",
  "data": {
    "referrals": [
      {
        "id": "c0a8012b-1234-5678-9999-aaaaaaaaaaaa",
        "invitedNumber": "99112233",
        "newNumber": null,
        "status": "SENT",
        "operatorName": "Mobicom",
        "expireDate": "2026-01-29T14:28:21"
      }
    ],
    "hasActiveEntitlement": false,
    "successReferralsCount": 0,
    "entitlementExpirationDate": "2026-02-25T00:00:00"
  }
}
```

---

### 3) Get General Info (DSD)
**GET** `/getGeneralInfo?msisdn=...&tokiId=...`

```sh
curl -X GET "http://10.21.68.222:6989/getGeneralInfo?msisdn=88112233&tokiId=TOKI_ID_123" \
  -H "Accept: application/json"
```

Response shape is the same as `/getInfo` (returns `CustomResponse<GetInfoData>`).

---

### 4) Send Invite (JWT)
**POST** `/sendInvite`

```sh
curl -X POST "http://10.21.68.222:6989/sendInvite" \
  -H "Authorization: Bearer <JWT_TOKEN_STRING>" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "msisdn": "99112233"
  }'
```

Example success response (shape):
```json
{
  "result": "Success",
  "message": "Invitation sent"
}
```

Example failure response (already invited / accepted):
```json
{
  "result": "fail",
  "message": "99112233 дугаарт урилга илгээгдсэн байна."
}
```

---

## Dev Run (Quarkus)

```sh
./mvnw quarkus:dev
```

---

## Notes

- If PostgreSQL tables use `DEFAULT gen_random_uuid()` for `ID`, you can omit setting `ID` in jOOQ inserts and still use `.returning()` to get generated values back.
- API paths are defined in `src/main/java/mn/unitel/campaign/Resources.java`.