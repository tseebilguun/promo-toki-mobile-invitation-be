# promo-toki-mobile-invitation-be

Toki Mobile referral invitation кампаний backend (Quarkus + PostgreSQL + jOOQ).

## Tech stack
- Java / Quarkus
- PostgreSQL
- jOOQ (DB access)
- MicroProfile Rest Client (external services)
- RabbitMQ consumer (legacy integration)
- Quarkus Scheduler (every 60s background jobs)

## Base URL
- `http://<host>:6989`

## Configuration (`src/main/resources/application.properties`)
Required key settings (main ones):

- Server
    - `quarkus.http.port=6989`
    - `quarkus.http.host=0.0.0.0`

- DB
    - `quarkus.datasource.jdbc.url=...`
    - `quarkus.datasource.username=...`
    - `quarkus.datasource.password=...`
    - `quarkus.datasource.jdbc.additional-jdbc-properties.currentSchema=toki_mobile_invitation`
    - `quarkus.jooq.dialect=POSTGRES`

- External services
    - `toki.user.client/mp-rest/url=...`
    - `toki.general/mp-rest/url=...`
    - `dsd.client/mp-rest/url=http://10.21.69.86:8090`

- Campaign / Debug
    - `campaign.start.date=...`
    - `campaign.end.date=...`
    - `campaign.debug.mode=true|false`
    - `campaign.test.numbers=...`

- Offers allow-list (RabbitMQ events)
    - `recharge.noti.accepted.offers=34109,34110,34111,34112`
    - `status.noti.accepted.offers=Entry_34104,Entry_34105,Entry_34106,Entry_34107`

- Links (used in SMS/push notifications)
    - `link.purchase.number=https://link.toki.mn/CX5z`
    - `link.referral.number=https://toki.mn/promo/referral`

## Main business flow

### 1) Invitation lifecycle (`REFERRAL_INVITATIONS`)
- `sendInvite` inserts a row with `STATUS='SENT'` and `EXPIRES_AT` (default `NOW()+72 hours` in DB).
- Scheduler periodically:
    - updates `SENT -> EXPIRED` when expired
    - sends reminder 24h before expiry
    - sends messages after expiry

### 2) Activation event (RabbitMQ status change)
When a user becomes Active (from legacy status stream):

- The service calls DSD `/third-party/user-id?msisdn=...` to get `receiver_toki_id`
- Then it updates `REFERRAL_INVITATIONS`:
    - `receiver_new_msisdn = msisdn`
    - `accepted_at = now`
    - `status = ACCEPTED`
    - only if still `SENT` and not expired

- Then it upserts/extends entitlements in `PROMOTION_ENTITLEMENTS` for:
    - Sender (`promo_type=Referrer`)
    - Receiver (`promo_type=Referee`)

- Then it sends SMS notifications to both sides.

### 3) Recharge event (RabbitMQ recharge)
When a recharge event arrives:
- Validates number is a Toki number
- Checks there is an active entitlement window (`START_AT <= now <= END_AT`)
- Determines bonus data plan from `MobileDataPlan`
- Calls legacy API (`APIUtil.addDeleteProduct`) to add product
- Inserts recharge history into `RECHARGES`
- Sends confirmation SMS on success

## Schedulers (every 60 seconds)
Implemented in `mn.unitel.campaign.Scheduler`:

- Update expired invitations (`SENT -> EXPIRED`)
- Notify 24h before invitation expires (SMS)
- Notify right after invitation expires (SMS + push)
- Notify right after entitlement expires (SMS + push)
- Notify 3 days before entitlement expires (push only)
- Notify 7 days before entitlement expires (push only)

## External API: DSD client
Endpoint:
- `GET /third-party/user-id?msisdn={MSISDN}`

Returns (examples):
- success: `result.userId`
- fail: `result.reason`, `result.detail`

## Running locally
```bash
./mvnw quarkus:dev
```

## Notes
- This project currently relies on time-window schedulers for “send once” reminders (no DB flag columns).
- Ensure `RECHARGES` insert types match DB column types (integers vs strings).