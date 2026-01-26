# promo-toki-mobile-invitation-be

Backend service for **Toki Mobile referral invitation campaign**.

This service is built with **Quarkus** and uses **jOOQ** to access a PostgreSQL database.  
It exposes a small REST API used by the client to:

- **Login** and receive a JWT (used for authenticated endpoints)
- **Get referral info** for the logged-in user (sent invitations, counts, entitlement status)
- **Send invitation** to a receiver MSISDN (also triggers push notification + SMS)

---

## Tech Stack

- Java + Quarkus
- JAX-RS (REST endpoints)
- jOOQ (DB access)
- PostgreSQL
- MicroProfile Rest Client (Toki external APIs)
- JWT-based auth (token returned from `/login`)

---

## Main Flow (Referral Program)

### Send Invite
1. User calls `POST /sendInvite` with receiver `msisdn`
2. Service checks if there is already an active invitation for that receiver:
    - counts rows where `RECEIVER_MSISDN = receiverMsisdn` and `STATUS IN ('SENT','ACCEPTED')`
3. Service fetches receiver and sender Toki user info via `TokiService`
4. Service inserts a row into `REFERRAL_INVITATIONS` with `STATUS='SENT'`
    - `SENT_AT` is set by DB default
    - `EXPIRES_AT` is set by DB default (`NOW() + 72 hours`)
5. Sends:
    - Push notification (Toki General API)
    - SMS message (legacy SMS service)

### Get Info
1. User calls `GET /getInfo`
2. Service reads `jwt.msisdn` and `jwt.tokiId` from request context
3. Fetches all invitations for the sender (`SENDER_TOKI_ID` + `SENDER_MSISDN`)
4. Builds response:
    - `referrals`: list of sent invitations
    - `successReferralsCount`: count of rows with `STATUS='ACCEPTED'`
    - entitlement fields based on latest `PROMOTION_ENTITLEMENTS.END_AT` for (`TOKI_ID`, `MSISDN`)
        - If `END_AT > now`: `hasActiveEntitlement = true`
        - Else: `hasActiveEntitlement = false`
        - `entitlementExpirationDate = END_AT` (even when expired)

---

## Database Tables (High Level)

- `REFERRAL_INVITATIONS`: sent/accepted/expired invitations, 72h expiry default
- `PROMOTION_ENTITLEMENTS`: promo entitlement period for user (start/end)
- `RECHARGES`: recharge history (bonus info, etc.)

---

## Running the application (dev)

```sh
./mvnw quarkus:dev
```

Default:
- App: `http://localhost:8080`
- Dev UI (dev mode only): `http://localhost:8080/q/dev/`

---

## API

Base URL: `http://localhost:8080`

### 1) Login (get JWT)
**POST** `/login`  
Body: `LoginReq`

```sh
curl -X POST "http://localhost:8080/login" \
  -H "Content-Type: application/json" \
  -d '{
    "msisdn": "88112233",
    "tokiId": "TOKI_ID_123"
  }'
```

Example success response (shape):
```json
{
  "result": "success",
  "message": "Login successful",
  "data": "<JWT_TOKEN_STRING>"
}
```

---

### 2) Get referral info (authenticated)
**GET** `/getInfo`  
Auth: `Authorization: Bearer <token>`

```sh
curl -X GET "http://localhost:8080/getInfo" \
  -H "Authorization: Bearer <JWT_TOKEN_STRING>" \
  -H "Accept: application/json"
```

Example success response (shape):
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

Notes:
- `operatorName` is derived from MSISDN prefix in `Helper.getOperatorName(...)`
- `expireDate` comes from `REFERRAL_INVITATIONS.EXPIRES_AT`

---

### 3) Send invite (authenticated)
**POST** `/sendInvite`  
Auth: `Authorization: Bearer <token>`  
Body: `InvitationReq`

```sh
curl -X POST "http://localhost:8080/sendInvite" \
  -H "Authorization: Bearer <JWT_TOKEN_STRING>" \
  -H "Content-Type: application/json" \
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

Example failure response (already invited):
```json
{
  "result": "fail",
  "message": "99112233 дугаарт урилга илгээгдсэн байна."
}
```

---

## Notes / TODOs

- Consider removing manual UUID generation in inserts if DB default is enabled for `ID`:
    - PostgreSQL: `DEFAULT gen_random_uuid()` (requires `pgcrypto` extension)
- Consider using DB time (`current_timestamp`) for entitlement comparisons to avoid time drift.
- Add OpenAPI / Swagger for easier API discovery.

---