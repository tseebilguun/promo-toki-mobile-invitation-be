# promo-toki-mobile-invitation-be

**Toki Mobile**-ийн *referral invitation* (урилгын) кампанит ажлын backend сервис.

Энэ сервис нь **Quarkus** дээр ажилладаг бөгөөд өгөгдлийн сантай **jOOQ** ашиглан (PostgreSQL) холбогдоно. Дараах REST API-уудыг санал болгоно:

- **Нэвтрэх** (login) → JWT авах
- **Урилгын мэдээлэл авах** → илгээсэн урилгууд, тооллого, entitlement төлөв
- **Урилга илгээх** → receiver MSISDN-д урилга үүсгээд push notification + SMS явуулах
- **Ерөнхий мэдээлэл авах (DSD)** → JWT шаардахгүйгээр msisdn/tokiId-оор query хийж мэдээлэл авах

---

## Base URL

Доорх бүх жишээний URL:

- `http://10.21.68.222:6989`

---

## Ил API-ууд (`Resources.java`-аас)

- `POST /login`
- `GET /getInfo` *(JWT шаардлагатай)*
- `GET /getGeneralInfo` *(DSD зориулалттай, query params)*
- `POST /sendInvite` *(JWT шаардлагатай)*

---

## Нэвтрэлт / Authentication

1) `POST /login` дуудсанаар JWT токен авна (`data` талбарт string хэлбэрээр ирнэ).
2) JWT шаардлагатай endpoint-ууд дээр дараах header ашиглана:

`Authorization: Bearer <JWT_TOKEN>`

`GET /getInfo` болон `POST /sendInvite` нь JWT context-оос дараах утгуудыг уншина:
- `jwt.msisdn`
- `jwt.tokiId`

---

## Үндсэн логик (товч)

### 1) Урилга илгээх (`POST /sendInvite`)
- Receiver msisdn шалгана (8 оронтой байх ёстой)
- Receiver дугаарт өмнө нь урилга явсан эсэхийг шалгана:
    - `RECEIVER_MSISDN = receiverMsisdn` ба `STATUS IN ('SENT','ACCEPTED')` бол reject
- Sender/Receiver-ийн Toki мэдээллийг гадаад Toki сервистэй холбогдож авна (`TokiService`)
- `REFERRAL_INVITATIONS` хүснэгтэд `STATUS='SENT'` мөр insert хийнэ
    - `SENT_AT` default: `NOW()`
    - `EXPIRES_AT` default: `NOW() + 72 hours`
- Дараах мэдэгдлүүдийг явуулна:
    - Push notification (Toki General API)
    - SMS (legacy SMS service)

### 2) Мэдээлэл авах (`GET /getInfo`)
- Нэвтэрсэн хэрэглэгчийн илгээсэн бүх урилгуудыг авна
- Response-д:
    - `referrals`: урилгын жагсаалт (receiver msisdn, status, operatorName, expiresAt)
    - `successReferralsCount`: `STATUS='ACCEPTED'` тоо
    - Entitlement мэдээлэл:
        - `PROMOTION_ENTITLEMENTS`-ээс (`tokiId`, `msisdn`) хослолоор `END_AT desc` хамгийн сүүлийн мөрийг авна
        - Хэрвээ `END_AT > now` бол → `hasActiveEntitlement=true`
        - Үгүй бол → `hasActiveEntitlement=false`
        - `entitlementExpirationDate` нь хамгийн сүүлийн `END_AT`-г буцаана (хугацаа нь дууссан байсан ч гэсэн)

### 3) Ерөнхий мэдээлэл авах (`GET /getGeneralInfo`) (DSD)
- `/getInfo`-той ижил төрлийн мэдээлэл буцаана
- Гэхдээ JWT context ашиглахгүй, query параметрээр авна:
    - `msisdn`
    - `tokiId`
- DSD зориулалттай

---

## cURL Жишээ

### 1) Нэвтрэх
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

Жишээ хариу (ерөнхий хэлбэр):
```json
{
  "result": "success",
  "message": "Login successful",
  "data": "<JWT_TOKEN_STRING>"
}
```

---

### 2) Мэдээлэл авах (JWT)
**GET** `/getInfo`

```sh
curl -X GET "http://10.21.68.222:6989/getInfo" \
  -H "Authorization: Bearer <JWT_TOKEN_STRING>" \
  -H "Accept: application/json"
```

Жишээ хариу (ерөнхий хэлбэр):
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

### 3) Ерөнхий мэдээлэл авах (DSD)
**GET** `/getGeneralInfo?msisdn=...&tokiId=...`

```sh
curl -X GET "http://10.21.68.222:6989/getGeneralInfo?msisdn=88112233&tokiId=TOKI_ID_123" \
  -H "Accept: application/json"
```

Хариу нь `/getInfo`-той ижил бүтэцтэй (`CustomResponse<GetInfoData>`).

---

### 4) Урилга илгээх (JWT)
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

Жишээ амжилттай хариу (ерөнхий хэлбэр):
```json
{
  "result": "Success",
  "message": "Invitation sent"
}
```

Жишээ алдаа (өмнө нь урилга илгээгдсэн / ACCEPTED болсон):
```json
{
  "result": "fail",
  "message": "99112233 дугаарт урилга илгээгдсэн байна."
}
```

---

## Хөгжүүлэлтийн орчинд ажиллуулах (Quarkus)

```sh
./mvnw quarkus:dev
```

---

## Тэмдэглэл

- Хэрвээ PostgreSQL хүснэгтүүд `ID` дээрээ `DEFAULT gen_random_uuid()` ашиглаж байгаа бол jOOQ insert хийх үед `ID`-г заавал set хийх шаардлагагүй. Мөн `.returning()` ашиглаад үүссэн мөрийн `ID`, `SENT_AT`, `EXPIRES_AT` зэрэг default утгуудыг буцааж авч болно.
- API замууд `src/main/java/mn/unitel/campaign/Resources.java` файл дээр тодорхойлогдсон.